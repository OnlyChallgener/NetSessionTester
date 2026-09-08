package com.demonv.netsessiontester.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demonv.netsessiontester.history.DesktopHistoryStore
import com.demonv.netsessiontester.model.*
import kotlinx.coroutines.*
import java.awt.Window
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

/** The same two-column grid, palette, chart and log rows as the live desktop dashboard. */
@Composable
internal fun DesktopHistoryPage(modifier: Modifier, window: Window?, onBack: () -> Unit) {
    val records by DesktopHistoryStore.records.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<AppMode?>(null) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<DesktopHistoryRecord?>(null) }
    var clearRequested by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    val visible = records.filter { record ->
        (filter == null || filter == record.appMode) &&
            (query.isBlank() || "${record.config.host}:${record.config.port} ${record.outcome}".contains(query.trim(), ignoreCase = true))
    }
    val selected = visible.firstOrNull { it.id == selectedId } ?: visible.firstOrNull()

    fun export(items: List<DesktopHistoryRecord>) {
        if (items.isEmpty() || exporting) return
        val chooser = JFileChooser().apply {
            dialogTitle = "导出测试记录"
            fileFilter = FileNameExtensionFilter("CSV 文件", "csv")
            selectedFile = File(if (items.size == 1) "NetSessionTester-${items.first().id}.csv" else "NetSessionTester-history.csv")
        }
        if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) return
        val picked = chooser.selectedFile
        val destination = if (picked.extension.equals("csv", true)) picked else File(picked.parentFile, picked.name + ".csv")
        if (destination.exists() && JOptionPane.showConfirmDialog(window, "替换已有文件 ${destination.name}？", "确认替换", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return
        exporting = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Files.writeString(destination.toPath(), DesktopHistoryStore.csv(items), StandardCharsets.UTF_8)
                }
                message = "已导出 ${items.size} 条记录"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = "导出失败：${error.message ?: "无法写入文件"}"
            } finally { exporting = false }
        }
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(330.dp).fillMaxHeight().historySurface().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("测试历史", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text("${records.size} / 100", fontSize = 10.sp, color = TextSecondary)
            }
            DesktopCompactInput(query, { query = it }, placeholder = "搜索目标或结果", modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(null to "全部", AppMode.SESSION_HOLD to "并发", AppMode.PING_STANDALONE to "Ping", AppMode.UNDERLOAD_PING to "联动").forEach { (mode, label) ->
                    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(if (filter == mode) AppleBlue.copy(alpha = 0.1f) else InputBg)
                        .clickable { filter = mode }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(label, fontSize = 11.sp, color = if (filter == mode) AppleBlue else TextSecondary)
                    }
                }
            }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (records.isEmpty()) "完成测试后，记录会显示在这里" else "没有匹配的记录", fontSize = 11.sp, color = TextSecondary)
                }
            } else LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.id }) { record ->
                    val active = record.id == selected?.id
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (active) AppleBlue.copy(alpha = 0.07f) else InputBg)
                        .border(0.5.dp, if (active) AppleBlue.copy(alpha = 0.35f) else CardBorder, RoundedCornerShape(8.dp))
                        .clickable { selectedId = record.id }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(record.appMode.label, fontSize = 10.sp, color = if (active) AppleBlue else TextSecondary)
                            Text(historyTime(record.startedAtEpochMs, "MM-dd HH:mm"), fontSize = 10.sp, color = TextSecondary)
                        }
                        Text("${record.config.host}:${record.config.port}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${record.outcome} · ${record.durationMs / 1000}s", fontSize = 10.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            HorizontalDivider(color = CardBorder)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { export(visible) }, enabled = visible.isNotEmpty() && !exporting) { Text("导出列表", fontSize = 11.sp, color = AppleBlue) }
                TextButton(onClick = { clearRequested = true }, enabled = records.isNotEmpty()) { Text("清空历史", fontSize = 11.sp, color = TextSecondary) }
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回当前测试", fontSize = 11.sp, color = AppleBlue) }
        }
        if (selected == null) {
            Box(Modifier.weight(1f).fillMaxHeight().historySurface(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("查看测试记录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("选择左侧记录，回看指标、曲线与日志", fontSize = 11.5.sp, color = TextSecondary)
                }
            }
        } else key(selected.id) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxWidth().historySurface().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${selected.appMode.label} · ${selected.config.host}:${selected.config.port}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${historyTime(selected.startedAtEpochMs)} · ${selected.durationMs / 1000}s · ${selected.outcome}", fontSize = 10.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { export(listOf(selected)) }, enabled = !exporting) { Text("导出", fontSize = 11.sp, color = AppleBlue) }
                        TextButton(onClick = { deleting = selected }) { Text("删除", fontSize = 11.sp, color = TextSecondary) }
                    }
                    Text("${selected.config.mode.label} · 目标 ${selected.config.batchSize} CPS · Ping ${selected.pingIntervalMs}ms · 超时 ${selected.config.timeoutMs}ms", fontSize = 10.5.sp, color = TextSecondary)
                    if (selected.appMode != AppMode.PING_STANDALONE) {
                        Text("成功目标 ${selected.config.successLimit} · 失败上限 ${selected.config.failureLimit} · ${if (selected.config.keepConnectionsAfterStop) "配置保留连接" else "配置自动释放"}", fontSize = 10.5.sp, color = TextSecondary)
                    }
                    if (message.isNotEmpty()) Text(message, fontSize = 10.5.sp, color = AppleBlue)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (selected.appMode == AppMode.PING_STANDALONE) {
                        CompactMetricCard(Modifier.weight(1f), "完成探测", "${selected.pingStats.sentCount}", AppleBlue)
                        CompactMetricCard(Modifier.weight(1f), "平均延迟", if (selected.pingStats.receivedCount > 0) "${selected.pingStats.avgLatencyMs} ms" else "—", AppleOrange)
                    } else {
                        CompactMetricCard(Modifier.weight(1f), "峰值会话", "${selected.sessionStats.maxStableSessions}", AppleBlue)
                        CompactMetricCard(Modifier.weight(1f), "成功 / 失败", "${selected.sessionStats.totalSuccess} / ${selected.sessionStats.totalFailure}", ApplePurple)
                    }
                    CompactMetricCard(Modifier.weight(1f), "结束时活动", if (selected.appMode == AppMode.PING_STANDALONE) "—" else "${selected.sessionStats.activeSessions}", AppleGreen)
                    CompactMetricCard(Modifier.weight(1f), "探测失败率", if (selected.pingStats.sentCount > 0) String.format("%.1f%%", selected.pingStats.lossPercent) else "—", AppleOrange)
                }
                DesktopDualChartCard(selected.points, selected.appMode, selected.sessionStats, selected.pingStats, selected.config.successLimit, Modifier.fillMaxWidth().weight(1.3f))
                Column(Modifier.fillMaxWidth().weight(0.8f).historySurface().padding(12.dp)) {
                    Text("测试事件日志", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(selected.logs) { DesktopLogItem(it) }
                    }
                }
            }
        }
    }
    if (deleting != null || clearRequested) AlertDialog(
        onDismissRequest = { deleting = null; clearRequested = false },
        title = { Text(if (clearRequested) "清空全部历史？" else "删除这条记录？") },
        text = { Text("删除后无法恢复，已导出的文件不受影响。") },
        confirmButton = { TextButton(onClick = {
            val id = deleting?.id
            val all = clearRequested
            deleting = null; clearRequested = false
            scope.launch { if (all) DesktopHistoryStore.clear() else if (id != null) DesktopHistoryStore.delete(id) }
        }) { Text("删除", color = AppleRed) } },
        dismissButton = { TextButton(onClick = { deleting = null; clearRequested = false }) { Text("取消") } },
        containerColor = CardBg, titleContentColor = TextPrimary, textContentColor = TextSecondary
    )
}

private fun Modifier.historySurface(): Modifier = clip(RoundedCornerShape(12.dp)).background(CardBg)
    .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))

private fun historyTime(time: Long, pattern: String = "yyyy-MM-dd HH:mm:ss"): String =
    DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
