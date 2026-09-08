"""Python-only source/packaging checks and independent measurement fixtures.

These checks DO NOT compile or execute Kotlin, Objective-C or platform networking.
The lexical check catches unbalanced edits, not Kotlin type/signature errors.
Run: python -m unittest discover -s docs/validation -p 'test_*.py' -v
"""
from pathlib import Path
import math
import plistlib
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
IOS = ROOT / 'shared/src/iosMain/kotlin/com/demonv/netsessiontester/ios'
JVM = ROOT / 'shared/src/jvmMain/kotlin/com/demonv/netsessiontester'


def read(path):
    return path.read_text(encoding='utf-8-sig')


def balanced_kotlin(source):
    """Skip comments/strings while recursively scanning ${...} expressions."""
    n = len(source)

    def quoted(i, quote, raw=False):
        i += len(quote)
        while i < n:
            if source.startswith(quote, i):
                return i + len(quote)
            if not raw and source[i] == '\\':
                i += 2
            elif quote != "'" and source.startswith('${', i):
                i = code(i + 2, '}')
            else:
                i += 1
        raise AssertionError('Unterminated string')

    def code(i, end=None):
        while i < n:
            c = source[i]
            if source.startswith('//', i):
                j = source.find('\n', i)
                i = n if j < 0 else j + 1
            elif source.startswith('/*', i):
                depth = 1
                i += 2
                while depth and i < n:
                    if source.startswith('/*', i):
                        depth += 1
                        i += 2
                    elif source.startswith('*/', i):
                        depth -= 1
                        i += 2
                    else:
                        i += 1
                if depth:
                    raise AssertionError('Unterminated comment')
            elif source.startswith('"""', i):
                i = quoted(i, '"""', raw=True)
            elif c in '\"\'':
                i = quoted(i, c)
            elif c == '`':
                j = source.find('`', i + 1)
                if j < 0:
                    raise AssertionError('Unterminated identifier')
                i = j + 1
            elif c in '([{':
                i = code(i + 1, {'(': ')', '[': ']', '{': '}'}[c])
            elif c in ')]}':
                if c != end:
                    raise AssertionError(f'Unexpected {c} at line {source.count(chr(10), 0, i) + 1}')
                return i + 1
            else:
                i += 1
        if end:
            raise AssertionError(f'Missing {end}')
        return i

    code(0)


class SourceContracts(unittest.TestCase):
    def test_kotlin_edits_have_balanced_delimiters(self):
        for base in ('commonMain', 'iosMain', 'jvmMain'):
            for path in (ROOT / 'shared/src' / base).rglob('*.kt'):
                with self.subTest(file=str(path.relative_to(ROOT))):
                    balanced_kotlin(read(path))
        balanced_kotlin(read(ROOT / 'shared/build.gradle.kts'))

    def test_lexer_handles_nested_interpolation_and_comments(self):
        balanced_kotlin('val x = "${f("a", "${g()}")}" // }\n/* /* [ */ */')
        with self.assertRaises(AssertionError):
            balanced_kotlin('fun broken() { val x = "${f(1)}"')

    def test_ios_has_no_jvm_format_or_imports(self):
        for path in (ROOT / 'shared/src/iosMain').rglob('*.kt'):
            with self.subTest(file=path.name):
                text = read(path)
                self.assertNotRegex(text, r'(?m)^import (java|javax|android)\.')
                self.assertNotRegex(text, r'\.format\(')

    def test_chart_event_contracts(self):
        for path in (IOS / 'IosApp.kt', JVM / 'desktop/DesktopApp.kt'):
            with self.subTest(file=path.name):
                text = read(path)
                self.assertIn('hasPingSample', text)
                self.assertIn('appendPoint(active = 0', text)
                self.assertIn('chartSamples = emptyList()', text)
                self.assertNotRegex(text, r'elapsedSec\s*=')
                self.assertNotIn('delay(300)', text)
                self.assertIn('takeLast(2400)', text)
        for path in (IOS / 'IosModels.kt', JVM / 'model/DesktopModels.kt'):
            text = read(path)
            self.assertIn('elapsedMs: Long', text)
            self.assertIn('pingLatencyMs: Int? = null', text)
            self.assertIn('hasPingSample: Boolean = false', text)

    def test_shared_math_is_used_by_both_engines(self):
        for path in (IOS / 'IosTcpEngine.kt', JVM / 'engine/DesktopTcpTester.kt'):
            self.assertIn('CpsPacer(', read(path))
        for path in (IOS / 'IosTcpEngine.kt', JVM / 'engine/DesktopPingTester.kt'):
            self.assertIn('PingAccumulator()', read(path))
        common = read(ROOT / 'shared/build.gradle.kts').split('commonMain.dependencies {')[1].split('}', 1)[0]
        # The desktop/iOS Compose upgrade must not replace Android's existing UI dependencies.
        self.assertNotIn('implementation(compose.', common)
        android = read(ROOT / 'shared/build.gradle.kts').split('androidMain.dependencies {')[1].split('}', 1)[0]
        self.assertIn('implementation(compose.runtime)', android)

    def test_tools_are_wired_to_real_implementations(self):
        text = read(IOS / 'IosExtraScreens.kt')
        for tool in ('DNS', 'NAT', 'IPV6', 'TRACEROUTE', 'MTU', 'IPERF', 'LOADED'):
            self.assertRegex(text, rf'IosTool\.{tool} -> Ios\w+Tools\.\w+\(')
        self.assertIn('IosHistoryStore.append(', text)
        self.assertIn('IosTaskRegistry.awaitIdle()', text)

    def test_registry_wait_is_bounded_without_starting_over_old_jobs(self):
        text = read(IOS / 'IosPersistence.kt')
        self.assertIn('withTimeoutOrNull(15_000L)', text)
        self.assertIn('check(stopped)', text)
        self.assertIn('filterNot { it.isCompleted }', text)
        self.assertIn('.distinct()', text)

    def test_native_factory_and_system_glass(self):
        native = read(ROOT / 'desktop/launcher/ios_main.m')
        kotlin = read(IOS / 'IosExtraScreens.kt')
        self.assertIn('name = "NSTAppFactory", exact = true', kotlin)
        for selector in re.findall(r'\[NSTAppFactory\.shared (\w+)\]', native):
            self.assertIn(f'fun {selector}()', kotlin)
        self.assertIn('UITabBarController', native)
        self.assertIn('initWithWindowScene:', native)
        self.assertIn('sceneDidEnterBackground:', native)
        for override in ('UITabBarAppearance', 'UIGlassEffect', 'backgroundEffect', 'UIRequiresFullScreen'):
            self.assertNotIn(override, native)

    def test_embedded_plist_and_minimal_permissions(self):
        workflow = read(ROOT / '.github/workflows/multiplatform.yml')
        xml = re.search(r'<\?xml.*?</plist>', workflow, re.S).group(0)
        plist = plistlib.loads(xml.encode('utf-8'))
        self.assertEqual(plist['MinimumOSVersion'], '15.0')
        scene = plist['UIApplicationSceneManifest']
        self.assertFalse(scene['UIApplicationSupportsMultipleScenes'])
        self.assertEqual(scene['UISceneConfigurations']['UIWindowSceneSessionRoleApplication'][0]['UISceneDelegateClassName'], 'SceneDelegate')
        for key in ('NSLocalNetworkUsageDescription',):
            self.assertTrue(plist[key])
        self.assertNotIn('NSLocationWhenInUseUsageDescription', plist)
        self.assertNotIn('--entitlements', workflow)
        self.assertIn("xcode-version: '26.0'", workflow)

    def test_desktop_history_uses_existing_components_and_finishes_before_save(self):
        page = read(JVM / 'desktop/DesktopHistoryPage.kt')
        app = read(JVM / 'desktop/DesktopApp.kt')
        main = read(JVM / 'desktop/Main.kt')
        for shared in ('DesktopCompactInput(', 'CompactMetricCard(', 'DesktopDualChartCard(', 'DesktopLogItem('):
            self.assertIn(shared, page)
        self.assertIn('Modifier.width(330.dp)', page)
        self.assertIn('onCloseRequest = { closeRequested = true }', main)
        final = app.split('withContext(NonCancellable + Dispatchers.Main)', 1)[1]
        self.assertLess(final.index('sessionTester.release()'), final.index('DesktopHistoryStore.append('))
        self.assertIn('logs = logs.toList()', final)
        self.assertIn('testJob?.cancelAndJoin()', app)

    def test_obsolete_desktop_launcher_and_chart_type_are_removed(self):
        self.assertFalse((ROOT / 'desktop/launcher/Launcher.cs').exists())
        self.assertNotIn('desktopJar', read(ROOT / 'shared/build.gradle.kts'))
        self.assertNotIn('data class ChartPoint(', read(JVM / 'model/DesktopModels.kt'))

    def test_self_contained_desktop_packaging(self):
        build = read(ROOT / 'shared/build.gradle.kts')
        workflow = read(ROOT / '.github/workflows/multiplatform.yml')
        self.assertIn('includeAllModules = true', build)
        self.assertIn(':shared:packageDmg', workflow)
        self.assertIn(':shared:createDistributable', workflow)
        self.assertIn('recursesubdirs', read(ROOT / 'installer.iss'))
        self.assertNotIn('|| true', workflow)


class IndependentMeasurementFixtures(unittest.TestCase):
    """Specification examples, not execution of the Kotlin implementation."""
    def test_subsecond_probe_gaps_and_family_boundaries(self):
        events = [(13, 29, 4), (513, 6, 4), (1013, None, 4), (1513, 9, 4), (2013, 8, 6)]
        segments = [(a, b) for a, b in zip(events, events[1:])
                    if a[1] is not None and b[1] is not None and a[2] == b[2]]
        self.assertEqual(len(segments), 1)
        self.assertNotEqual(events[0][0] / 1000.0, events[1][0] / 1000.0)
        self.assertEqual(events[0][0] // 1000, events[1][0] // 1000)  # original x-axis failure

    def test_ping_failure_is_not_zero_or_reused_success(self):
        samples = [10, 14, None, 22, 28, None]
        success = [x for x in samples if x is not None]
        adjacent = [abs(a-b) for a, b in zip(samples, samples[1:]) if a is not None and b is not None]
        self.assertIsNone(samples[-1])
        self.assertEqual(sum(success) // len(success), 18)
        self.assertEqual(sum(adjacent) // len(adjacent), 5)
        self.assertAlmostEqual((len(samples)-len(success))*100/len(samples), 100/3)

    def test_cps_stall_does_not_replay_unbounded_burst(self):
        cps = 500
        elapsed_ms = 5000
        burst = max(1, cps * 100 / 1000)
        due = math.floor(min(burst, cps * elapsed_ms / 1000))
        self.assertEqual(due, 50)
        self.assertEqual(min(due, 0), 0)


if __name__ == '__main__':
    unittest.main()
