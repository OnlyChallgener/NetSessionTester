"""Static source contracts and small lifecycle models for the iOS implementation.

These tests do not compile or execute Kotlin/iOS code. They are intentionally limited
to Python source inspection and deterministic models that can run on any workstation.
Real socket and cancellation behavior still needs an
iPhone build and device validation.
"""

from __future__ import annotations

import math
import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
IOS_ROOT = REPO_ROOT / "shared/src/iosMain/kotlin/com/demonv/netsessiontester/ios"
ENGINE = IOS_ROOT / "IosTcpEngine.kt"


def source(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def modeled_launch_count(cps: int, duration_ms: int = 10_000, step_ms: int = 20) -> int:
    """Mirror CpsPacer token arithmetic without executing Kotlin."""
    previous_ms = 0
    tokens = 0.0
    launched = 0
    for now_ms in range(step_ms, duration_ms + 1, step_ms):
        elapsed_ms = max(0, now_ms - previous_ms)
        previous_ms = max(previous_ms, now_ms)
        burst = max(1.0, cps * 100 / 1000.0)
        tokens = min(burst, tokens + cps * elapsed_ms / 1000.0)
        due = math.floor(tokens)
        tokens -= due
        launched += due
    return launched


class IosLifecycleStaticTests(unittest.TestCase):
    def test_cps_model_tracks_attempt_rate(self) -> None:
        for cps in (1, 10, 50, 500, 5000):
            with self.subTest(cps=cps):
                self.assertLessEqual(abs(modeled_launch_count(cps) - cps * 10), 1)

    def test_target_is_cumulative_success_not_live_refill(self) -> None:
        total_success = 0
        active = 0
        attempts = 0
        while total_success < 100:
            attempts += 1
            total_success += 1
            active += 1
            if total_success % 7 == 0:
                active -= 1
        self.assertEqual((total_success, active, attempts), (100, 86, 100))

        engine = source(ENGINE)
        run = engine.split("private suspend fun runOneProtocol", 1)[1].split(
            "suspend fun runStandalonePing", 1
        )[0]
        self.assertIn(
            "while (isActive && totalSuccess < config.successLimit",
            run,
        )
        self.assertNotIn("totalFailure += health.closed", run)

    def test_connect_is_cancellable_and_uses_resolved_addresses(self) -> None:
        engine = source(ENGINE)
        connect = engine.split("private suspend fun connectOnWorker", 1)[1].split(
            "private suspend fun probeResolved", 1
        )[0]
        run = engine.split("private suspend fun runOneProtocol", 1)[1].split(
            "suspend fun runStandalonePing", 1
        )[0]
        self.assertIn("poll(pollFd.ptr, 1u, 0)", connect)
        self.assertIn("delay(5L)", connect)
        self.assertIn("AI_NUMERICHOST or AI_NUMERICSERV", connect)
        self.assertIn("connectResolvedSingle(address", run)
        self.assertNotIn("connectSingle(config.host", run)

    def test_fd_ownership_and_non_cancellable_cleanup(self) -> None:
        engine = source(ENGINE)
        for required in (
            "handoff.fd?.let { close(it) }",
            "ownedFd?.let { close(it) }",
            "ownedOutcomeFd?.let { close(it) }",
            "pending.fd?.let { close(it) }",
            "heldSocketFds.forEach { close(it) }",
            "workers.joinAll()",
        ):
            with self.subTest(required=required):
                self.assertIn(required, engine)
        self.assertIn("withContext(NonCancellable)", engine)
        self.assertIn("results.trySend", engine)

    def test_final_stats_cover_fast_failure_and_use_matching_level(self) -> None:
        engine = source(ENGINE)
        run = engine.split("private suspend fun runOneProtocol", 1)[1].split(
            "suspend fun runStandalonePing", 1
        )[0]
        for field in (
            "totalSuccess = totalSuccess",
            "totalFailure = totalFailure",
            "totalAttempts = totalAttempts",
            "cps = 0",
            "averageConnectLatencyMs = if (totalSuccess == 0)",
        ):
            with self.subTest(field=field):
                self.assertIn(field, run)
        self.assertIn("if (reachedFailureLimit) IosLogLevel.ERROR", run)
        self.assertIn("reachedFailureLimit ->", run)

    def test_ping_cadence_and_failure_contract(self) -> None:
        engine = source(ENGINE)
        self.assertIn("PingAccumulator", engine)
        self.assertIn("currentLatencyMs = currentMs", engine)
        self.assertEqual(
            engine.count("config.pingIntervalMs - (getMonotonicMs() - cycleStartedAt)"),
            2,
        )

    def test_active_socket_health_consumes_bounded_data(self) -> None:
        engine = source(ENGINE)
        self.assertIn("while (reads < 4)", engine)
        self.assertIn(
            "recv(fd, pinned.addressOf(0), drainBuffer.size.convert(), MSG_DONTWAIT)",
            engine,
        )
        self.assertIn("received < 0L && errno == EINTR", engine)
        self.assertNotIn("MSG_PEEK or MSG_DONTWAIT", engine)

    def test_standalone_setup_is_inside_lifecycle_finally(self) -> None:
        engine = source(ENGINE)
        standalone = engine.split("suspend fun runStandalonePing", 1)[1].split(
            "fun stopTest", 1
        )[0]
        try_position = standalone.index("try {")
        self.assertGreater(standalone.index("resolveHost(config.host)"), try_position)
        self.assertGreater(standalone.index("onLog(log("), try_position)
        self.assertIn("activeRunJob = null", standalone)
        self.assertIn("setScreenKeepAwake(false)", standalone)



if __name__ == "__main__":
    unittest.main()
