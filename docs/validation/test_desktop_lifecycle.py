"""Static lifecycle contracts for the desktop network engines.

These tests do not compile or execute Kotlin and do not open network connections.
They inspect source ordering and exercise a small deterministic ownership model so
they can run in a Python-only validation environment.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
ENGINE_ROOT = REPO_ROOT / "shared/src/jvmMain/kotlin/com/demonv/netsessiontester/engine"
TCP = ENGINE_ROOT / "DesktopTcpTester.kt"
PING = ENGINE_ROOT / "DesktopPingTester.kt"


def source(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def section(text: str, start: str, end: str) -> str:
    return text.split(start, 1)[1].split(end, 1)[0]


class OwnershipModel:
    """Minimal model of discardPending's exactly-once local accounting."""

    def __init__(self) -> None:
        self.pending = {"socket"}
        self.held: set[str] = set()
        self.pending_count = 1
        self.success = 0

    def discard(self) -> bool:
        removed = "socket" in self.pending
        self.pending.discard("socket")
        self.held.discard("socket")
        if removed:
            self.pending_count -= 1
        return removed

    def transfer(self) -> None:
        if "socket" not in self.pending:
            raise RuntimeError("not pending")
        self.pending.remove("socket")
        self.pending_count -= 1
        self.held.add("socket")
        self.success += 1


class DesktopLifecycleStaticTests(unittest.TestCase):
    def test_ping_setup_is_inside_running_job_finally(self) -> None:
        run = section(source(PING), "suspend fun runContinuousPing", "/** Cancels the active loop")
        registration = run.index("runningJob = job")
        lifecycle_try = run.index("try {", registration)
        self.assertGreater(run.index("resolveAddresses(cleanHost, protocol)"), lifecycle_try)
        self.assertGreater(run.index("onLog("), lifecycle_try)
        self.assertIn("finally {\n            clearRunningJob(job)", run)
        self.assertEqual(run.count("clearRunningJob(job)"), 1)

    def test_tcp_selector_and_callbacks_are_inside_cleanup_boundary(self) -> None:
        run = section(source(TCP), "private suspend fun runOneProtocol", "/** Cancels the active run")
        lifecycle_try = run.index("try {")
        for operation in (
            "Selector.open()",
            "registerRunSelector(selector, expectedGeneration)",
            "onLog(LogLine(level = LogLevel.SUCCESS",
            "onStats(stats)",
        ):
            with self.subTest(operation=operation):
                self.assertGreater(run.index(operation, lifecycle_try), lifecycle_try)
        self.assertIn("val selector = openedSelector ?: return", run)
        self.assertIn("if (runResourcesCleaned) return", run)
        self.assertGreaterEqual(run.count("cleanupRunResources()"), 3)

    def test_pending_to_held_transfer_counts_success_last(self) -> None:
        run = section(source(TCP), "private suspend fun runOneProtocol", "/** Cancels the active run")
        finish = section(run, "fun finishConnected", "fun openOne")
        key_ready = finish.index("key.interestOps(SelectionKey.OP_READ)")
        ownership_swap = finish.index("val transferred = synchronized(stateLock)")
        local_pending_removed = finish.index("pendingThisRun.remove(channel)")
        success = finish.index("totalSuccess++")
        self.assertLess(key_ready, ownership_swap)
        self.assertLess(ownership_swap, local_pending_removed)
        self.assertLess(local_pending_removed, success)
        self.assertIn("pendingChannels.remove(channel)", finish)
        self.assertIn("heldChannels.getValue(protocol).add(channel)", finish)
        self.assertEqual(finish.count("discardPending(key, channel)"), 2)

    def test_pending_accounting_is_centralized_and_idempotent(self) -> None:
        run = section(source(TCP), "private suspend fun runOneProtocol", "/** Cancels the active run")
        discard = section(run, "fun discardPending", "fun finishConnected")
        self.assertIn("val removed = pendingThisRun.remove(channel)", discard)
        self.assertIn("if (removed) pendingCount--", discard)
        self.assertIn("heldChannels.getValue(protocol).remove(channel)", discard)

        failed = OwnershipModel()
        self.assertTrue(failed.discard())
        self.assertFalse(failed.discard())
        self.assertEqual(failed.pending_count, 0)
        self.assertEqual(failed.success, 0)

        connected = OwnershipModel()
        connected.transfer()
        self.assertEqual((connected.pending_count, connected.success), (0, 1))
        self.assertEqual(connected.held, {"socket"})

    def test_modified_kotlin_delimiters_are_balanced(self) -> None:
        literals_and_comments = re.compile(
            r'''("""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'''
            r'''\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*)'''
        )
        pairs = {")": "(", "]": "[", "}": "{"}
        for path in (TCP, PING):
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
