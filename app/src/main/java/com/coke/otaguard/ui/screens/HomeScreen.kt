package com.coke.otaguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coke.otaguard.data.LogEntry
import com.coke.otaguard.data.LogLevel
import com.coke.otaguard.data.OtaStatus
import com.coke.otaguard.data.PackageStatus
import com.coke.otaguard.data.SettingStatus
import com.coke.otaguard.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    otaStatus: OtaStatus?,
    isLoading: Boolean,
    logs: List<LogEntry>,
    onRefresh: () -> Unit,
    onEnforce: () -> Unit
) {
    var currentTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
    ) {
        when (currentTab) {
            0 -> GuardPage(otaStatus, isLoading, onRefresh, onEnforce)
            1 -> LogPage(logs)
        }

        TabBar(
            currentTab = currentTab,
            onTabSelect = { currentTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ========== 防护页 ==========

@Composable
private fun GuardPage(
    otaStatus: OtaStatus?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onEnforce: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 64.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { HeaderSection(otaStatus) }
        item { LspStatusCard(otaStatus) }
        item { StatusOverview(otaStatus) }
        item { ComponentSection(otaStatus) }
        item { SettingsSection(otaStatus) }
        item { ActionsSection(otaStatus, isLoading, onRefresh, onEnforce) }
    }
}

// ========== 日志页 ==========

@Composable
private fun LogPage(logs: List<LogEntry>) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp)
    ) {
        // 标题
        Text(
            text = "运行日志",
            color = White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 16.dp)
        )

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无日志", color = TextDim, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDark)
                    .border(1.dp, Border, RoundedCornerShape(16.dp)),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.INFO -> Green
        LogLevel.WARN -> Color(0xFFFBBF24)
        LogLevel.ERROR -> Red
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.timeStr,
            color = TextDim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(50.dp)
        )
        Text(
            text = entry.tag,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(34.dp)
        )
        Text(
            text = entry.message,
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ========== Header ==========

@Composable
private fun HeaderSection(otaStatus: OtaStatus?) {
    val safe = otaStatus?.overallSafe == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "OTA Guard",
            color = White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (otaStatus == null) "检查中..." else if (safe) "防护已启用" else "存在风险",
            color = if (safe) Green else Red,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ========== LSPosed 状态卡片 ==========

@Composable
private fun LspStatusCard(otaStatus: OtaStatus?) {
    val active = otaStatus?.moduleActive == true

    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) GreenDarkBg else RedDarkBg)
            .border(1.dp, if (active) Green.copy(alpha = 0.3f) else Red.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Extension,
                contentDescription = null,
                tint = if (active) Green else Red,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "LSPosed 框架",
                    color = White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (active) "模块已激活，Hook 防护运行中" else "模块未激活，仅 Root 防护可用",
                    color = if (active) Green.copy(alpha = 0.8f) else Red.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
        }
        StatusBadge(if (active) "已激活" else "未激活", active)
    }
}

// ========== Status Overview ==========

@Composable
private fun StatusOverview(otaStatus: OtaStatus?) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val disabledCount = otaStatus?.packages?.count { it.isDisabled } ?: 0
        val totalPkg = otaStatus?.packages?.size ?: 5
        val correctSettings = otaStatus?.settings?.count { it.isCorrect } ?: 0
        val totalSettings = otaStatus?.settings?.size ?: 3

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 组件卡片
            MetricCard(
                value = "$disabledCount/$totalPkg",
                label = "组件已冻结",
                ok = disabledCount == totalPkg,
                modifier = Modifier.weight(1f)
            )
            // 设置卡片
            MetricCard(
                value = "$correctSettings/$totalSettings",
                label = "设置已锁定",
                ok = correctSettings == totalSettings,
                modifier = Modifier.weight(1f)
            )
            // Root 卡片
            MetricCard(
                value = if (otaStatus?.hasRoot == true) "✓" else "✗",
                label = "Root",
                ok = otaStatus?.hasRoot == true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    ok: Boolean
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardDark)
            .border(1.dp, Border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            color = if (ok) Green else Red,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ========== 组件状态 ==========

@Composable
private fun ComponentSection(otaStatus: OtaStatus?) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("组件状态", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("OTA 相关系统组件冻结情况", color = TextDim, fontSize = 11.sp)
        }

        val packages = otaStatus?.packages ?: emptyList()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardDark)
                .border(1.dp, Border, RoundedCornerShape(20.dp))
        ) {
            packages.forEachIndexed { index, pkg ->
                PackageRow(pkg, isLast = index == packages.lastIndex)
            }
        }
    }
}

@Composable
private fun PackageRow(pkg: PackageStatus, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isLast) Modifier.drawBehind {
                    drawLine(Color(0xFF27272A), Offset(0f, size.height), Offset(size.width, size.height), 1f)
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(pkg.label, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(pkg.packageName, color = TextDim, fontSize = 10.sp)
            Text(pkg.description, color = TextDark, fontSize = 10.sp)
        }
        Spacer(Modifier.width(12.dp))
        StatusBadge(if (pkg.isDisabled) "已冻结" else "运行中", pkg.isDisabled)
    }
}

// ========== 系统设置 ==========

@Composable
private fun SettingsSection(otaStatus: OtaStatus?) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("系统设置", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Global Settings", color = TextDim, fontSize = 11.sp)
        }

        val settings = otaStatus?.settings ?: emptyList()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardDark)
                .border(1.dp, Border, RoundedCornerShape(20.dp))
        ) {
            settings.forEachIndexed { index, setting ->
                SettingRow(setting, isLast = index == settings.lastIndex)
            }
        }
    }
}

@Composable
private fun SettingRow(setting: SettingStatus, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isLast) Modifier.drawBehind {
                    drawLine(Color(0xFF27272A), Offset(0f, size.height), Offset(size.width, size.height), 1f)
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(setting.label, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(setting.key, color = TextDim, fontSize = 10.sp)
            Text("当前：${setting.currentValue} | 期望：${setting.expectedValue}", color = TextDark, fontSize = 10.sp)
        }
        Spacer(Modifier.width(12.dp))
        StatusBadge(if (setting.isCorrect) "正常" else "异常", setting.isCorrect)
    }
}

// ========== Badge ==========

@Composable
private fun StatusBadge(text: String, ok: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (ok) GreenDarkBg else RedDarkBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (ok) Green else Red, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ========== Actions ==========

@Composable
private fun ActionsSection(
    otaStatus: OtaStatus?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onEnforce: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .border(1.dp, Border, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = White,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = TextDim
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TextMuted)
                    } else {
                        Icon(Icons.Outlined.Refresh, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("刷新检查", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = onEnforce,
                enabled = !isLoading,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green,
                    contentColor = GreenDarkBg,
                    disabledContainerColor = Green.copy(alpha = 0.4f),
                    disabledContentColor = GreenDarkBg
                )
            ) {
                Icon(Icons.Outlined.Shield, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("强制封锁", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (otaStatus != null) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            Text(
                text = "上次检查：${sdf.format(Date(otaStatus.lastCheckTime))}",
                color = TextDark,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ========== Tab Bar ==========

@Composable
private fun TabBar(currentTab: Int, onTabSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, BgBlack), startY = 0f, endY = 60f)
            )
            .padding(horizontal = 32.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(CardDark)
                .border(1.dp, Border, RoundedCornerShape(100.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            TabItem(
                icon = Icons.Outlined.Shield,
                label = "防护",
                active = currentTab == 0,
                onClick = { onTabSelect(0) },
                modifier = Modifier.weight(1f)
            )
            TabItem(
                icon = Icons.Outlined.Description,
                label = "日志",
                active = currentTab == 1,
                onClick = { onTabSelect(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon, null,
            tint = if (active) Green else TextDim,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (active) Green else TextDim,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
