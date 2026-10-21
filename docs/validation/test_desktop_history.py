"""Static contracts for desktop history persistence.

These tests do not compile or execute Kotlin. They perform Python-only source checks
and deterministic models; no application data files or network resources are used.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MODEL = REPO_ROOT / "shared/src/jvmMain/kotlin/com/demonv/netsessiontester/model/DesktopHistoryRecord.kt"
STORE = REPO_ROOT / "shared/src/jvmMain/kotlin/com/demonv/netsessiontester/history/DesktopHistoryStore.kt"


def source(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class DesktopHistoryStaticTests(unittest.TestCase):
    def test_record_contains_complete_snapshot_contract(self) -> None:
        model = source(MODEL)
        for declaration in (
            "val id: Long",
            "val startedAtEpochMs: Long",
            "val durationMs: Long",
            "val outcome: String",
            "val appMode: AppMode",
            "val config: SessionConfig",
            "val pingIntervalMs: Long",
            "val sessionStats: ProtocolStats",
            "val pingStats: PingStats",
            "val points: List<DualChartPoint>",
            "val logs: List<LogLine>",
        ):
            with self.subTest(declaration=declaration):
                self.assertIn(declaration, model)

    def test_public_store_api_and_limits(self) -> None:
        store = source(STORE)
        for declaration in (
            "val records: StateFlow<List<DesktopHistoryRecord>>",
            "val error: StateFlow<String?>",
            "suspend fun load()",
            "suspend fun append(record: DesktopHistoryRecord)",
            "suspend fun delete(id: Long)",
            "suspend fun clear()",
            "fun csv(records: List<DesktopHistoryRecord>): String",
            "private const val MAX_RECORDS = 100",
            "private const val MAX_POINTS_PER_RECORD = 2_400",
            "private const val MAX_LOGS_PER_RECORD = 600",
        ):
            with self.subTest(declaration=declaration):
                self.assertIn(declaration, store)

    def test_load_append_are_serialized_and_initialized_once(self) -> None:
        store = source(STORE)
        append = store.split("suspend fun append", 1)[1].split("suspend fun delete", 1)[0]
        self.assertIn("private val mutex = Mutex()", store)
        self.assertIn("private var initialized = false", store)
        self.assertIn("if (initialized) return", store)
        self.assertIn("initialized = true", store)
        self.assertIn("mutex.withLock", append)
        self.assertLess(append.index("ensureLoadedLocked()"), append.index("_records.value = candidate"))

    def test_append_assigns_unique_monotonic_id_and_keeps_memory_on_write_failure(self) -> None:
        store = source(STORE)
        append = store.split("suspend fun append", 1)[1].split("suspend fun delete", 1)[0]
        self.assertIn("maxOf(Math.addExact(lastId, 1L), record.id)", append)
        memory_commit = append.index("_records.value = candidate")
        disk_write = append.index("writeAtomically(candidate)")
        error_report = append.index('_error.value = failureMessage("保存历史记录", failure)')
        self.assertLess(memory_commit, disk_write)
        self.assertLess(disk_write, error_report)

    def test_delete_and_clear_commit_only_after_successful_write(self) -> None:
        store = source(STORE)
        helper = store.split("private inline fun persistOrReport", 1)[1].split(
            "private fun writeAtomically", 1
        )[0]
        self.assertLess(helper.index("writeAtomically(candidate)"), helper.index("commit()"))

    def test_atomic_replace_and_corrupt_backup_protection(self) -> None:
        store = source(STORE)
        for contract in (
            "FileChannel.open(temporary, StandardOpenOption.WRITE)",
            "channel.force(true)",
            "StandardCopyOption.ATOMIC_MOVE",
            "StandardCopyOption.REPLACE_EXISTING",
            'history.corrupt.${System.currentTimeMillis()}.json',
            "preserveCorruptFile = backup == null",
            'check(!preserveCorruptFile) { "损坏的历史文件尚未成功备份" }',
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, store)
        self.assertNotIn("ObjectOutputStream", store)
        self.assertNotIn("java.io.Serializable", store)

    def test_platform_paths_and_csv_safety(self) -> None:
        store = source(STORE)
        for contract in (
            'System.getenv("LOCALAPPDATA")',
            '"Library", "Application Support"',
            'return "\\uFEFF" + rows.joinToString(separator = "\\r\\n", postfix = "\\r\\n")',
            '"\\\"" + safe.replace("\\\"", "\\\"\\\"") + "\\\""',
            "first == '=' || first == '+' || first == '-' || first == '@'",
            'commonCsvCells(record, "point")',
            'commonCsvCells(record, "log")',
            "number(point.elapsedMs)",
            "text(log.text)",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, store)

    def test_modified_kotlin_delimiters_are_balanced(self) -> None:
        literals_and_comments = re.compile(
            r'''("""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'''
            r'''\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*)'''
        )
        pairs = {")": "(", "]": "[", "}": "{"}
        for path in (MODEL, STORE):
            with self.subTest(path=path.name):
                text = literals_and_comments.sub(
                    lambda match: "\n" * match.group(0).count("\n"), source(path)
                )
                stack: list[str] = []
                for char in text:
                    if char in "([{":
                        stack.append(char)
                    elif char in pairs:
                        self.assertTrue(stack, f"unexpected {char}")
                        self.assertEqual(stack.pop(), pairs[char])
                self.assertEqual(stack, [])


if __name__ == "__main__":
    unittest.main()
