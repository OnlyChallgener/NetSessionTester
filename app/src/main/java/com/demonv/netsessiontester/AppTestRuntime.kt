package com.demonv.netsessiontester

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-level test runtime.
 *
 * The TCP session growth loop must not be tied to Activity/Compose lifecycle.
 * Keeping the core loop here lets it continue while the app is in background,
 * as long as the foreground service is alive.
 */
object AppTestRuntime {
    /** Core test loop scope. Never tie this to Activity lifecycle. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Non-blocking UI/log delivery scope. The test loop must not wait for Compose redraws while backgrounded. */
    val mainScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
