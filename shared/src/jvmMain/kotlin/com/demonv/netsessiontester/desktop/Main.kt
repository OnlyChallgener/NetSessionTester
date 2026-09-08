package com.demonv.netsessiontester.desktop

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "NetSessionTester - 全平台网络会话与并发测试",
        state = rememberWindowState(
            width = 1100.dp,
            height = 760.dp,
            position = WindowPosition(Alignment.Center)
        )
    ) {
        DesktopApp()
    }
}
