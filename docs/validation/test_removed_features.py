"""Static Android source checks for features that have been deliberately removed.

These tests inspect source text only.  They do not compile or execute Kotlin and are
not a substitute for an Android build or device test.
"""

from pathlib import Path
import unittest


REPO_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SOURCE = REPO_ROOT / "app" / "src" / "main"
MAIN_ACTIVITY = (
    ANDROID_SOURCE
    / "java"
    / "com"
    / "demonv"
    / "netsessiontester"
    / "MainActivity.kt"
)


class RemovedFeatureSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.main_source = MAIN_ACTIVITY.read_text(encoding="utf-8")
        cls.android_source = "\n".join(
            path.read_text(encoding="utf-8")
            for path in sorted(ANDROID_SOURCE.rglob("*"))
            if path.suffix in {".kt", ".java"}
        )

    def test_dual_network_implementation_symbols_are_gone(self) -> None:
        removed_symbols = (
            "AppToolPage.DUAL_NETWORK",
            "DualNetworkToolPage",
            "DualNetworkChart",
            "DualNetSample",
            "DualNetSummary",
            "DualNetHistoryRecord",
            "probeNetworkRtt",
            "loadDualNet",
            "addDualNet",
            "deleteDualNet",
            "dualNetSizeKb",
            "dualnet_history",
            "NetworkRequest",
        )
        for symbol in removed_symbols:
            with self.subTest(symbol=symbol):
                self.assertNotIn(symbol, self.android_source)

    def test_dual_network_custom_target_key_is_gone(self) -> None:
        self.assertNotIn('"dualnet"', self.android_source)

    def test_shared_android_tools_remain_available(self) -> None:
        retained_symbols = (
            "RoamingToolPage",
            "CellularInfoToolPage",
            "IperfToolPage",
            "WinnerVerdict",
            "loadCustomTargets",
            "NetworkCapabilities",
        )
        for symbol in retained_symbols:
            with self.subTest(symbol=symbol):
                self.assertIn(symbol, self.main_source)


if __name__ == "__main__":
    unittest.main()
