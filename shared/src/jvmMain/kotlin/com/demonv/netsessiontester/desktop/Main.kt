package com.demonv.netsessiontester.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import javax.imageio.ImageIO

fun main() = application {
    var closeRequested by remember { mutableStateOf(false) }
    val isWindows = remember {
        System.getProperty("os.name", "").lowercase().contains("windows")
    }

    val iconPainter = remember {
        runCatching {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
                ?: File("desktop/assets/icon.png").takeIf { it.exists() }?.inputStream()
            stream?.use { ImageIO.read(it)?.toComposeImageBitmap() }?.let { BitmapPainter(it) }
        }.getOrNull()
    }

    val windowState = rememberWindowState(
        width = 1100.dp,
        height = 740.dp,
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = { closeRequested = true },
        title = "NetSessionTester",
        icon = iconPainter,
        undecorated = isWindows,
        resizable = true,
        state = windowState
    ) {
        DesktopApp(
            windowState = windowState,
            window = this.window,
            onClose = ::exitApplication,
            closeRequested = closeRequested,
            isWindows = isWindows
        )
    }
}
