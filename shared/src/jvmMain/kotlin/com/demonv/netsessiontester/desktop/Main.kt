package com.demonv.netsessiontester.desktop

import androidx.compose.runtime.remember
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
    val iconPainter = remember {
        runCatching {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
                ?: File("desktop/assets/icon.png").takeIf { it.exists() }?.inputStream()
            stream?.use { ImageIO.read(it)?.toComposeImageBitmap() }?.let { BitmapPainter(it) }
        }.getOrNull()
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "NetSessionTester",
        icon = iconPainter,
        state = rememberWindowState(
            width = 1060.dp,
            height = 720.dp,
            position = WindowPosition(Alignment.Center)
        )
    ) {
        DesktopApp()
    }
}
