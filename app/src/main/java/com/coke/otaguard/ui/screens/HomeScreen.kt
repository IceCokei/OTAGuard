package com.coke.otaguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onRefresh: () -> Unit,
    onEnforce: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            item { HeaderSection(otaStatus) }

            // Status Overview
            item { StatusOverview(otaStatus) }

            // 组件状态
            item { ComponentSection(otaStatus) }

            // 系统设置
            item { SettingsSection(otaStatus) }

            // 操作按钮
            item { ActionsSection(otaStatus, isLoading, onRefresh, onEnforce) }
        }

        // 底部 Tab Bar
        TabBar(modifier = Modifier.align(Alignment.BottomCenter))
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CardDark)
                .border(1.dp, Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
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

        Text(
            text = "OTA Guard $disabledCount/$totalPkg",
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (otaStatus?.overallSafe == true)
                "防护已启用 · 所有 OTA 更新通道已封锁"
            else "存在风险 · 部分更新通道未被禁用",
            color = TextMuted,
            fontSize = 12.sp
        )

        // 三个指标卡片
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                value = "$disabledCount/$totalPkg",
                label = "组件",
                ok = disabledCount == totalPkg,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                value = "$correctSettings/$totalSettings",
                label = "可用",
                sub = "设置",
                ok = correctSettings == totalSettings,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                value = null,
                label = "Root",
                statusText = if (otaStatus?.hasRoot == true) "Root 权限可用" else "Root 不可用",
                ok = otaStatus?.hasRoot == true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    value: String? = null,
    label: String,
    sub: String? = null,
    statusText: String? = null,
    ok: Boolean
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardDark)
            .border(1.dp, Border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (value != null) {
            Text(
                text = value,
                color = if (ok) Green else Red,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        if (sub != null) {
            Text(text = sub, color = TextDim, fontSize = 10.sp)
        }
        if (statusText != null) {
            Text(
                text = statusText,
                color = if (ok) Green else Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ========== 组件状态 ==========

@Composable
private fun ComponentSection(otaStatus: OtaStatus?) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "组件状态",
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "OTA 相关系统组件冻结情况",
                color = TextDim,
                fontSize = 11.sp
            )
        }

        // Card with rows
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
                    val y = size.height
                    drawLine(
                        color = Color(0xFF27272A),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = pkg.label,
                color = White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = pkg.packageName,
                color = TextDim,
                fontSize = 10.sp
            )
            Text(
                text = pkg.description,
                color = TextDark,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        // Badge
        StatusBadge(
            text = if (pkg.isDisabled) "已冻结" else "运行中",
            ok = pkg.isDisabled
        )
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
            Text(
                text = "系统设置",
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Global Settings",
                color = TextDim,
                fontSize = 11.sp
            )
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
                    val y = size.height
                    drawLine(
                        color = Color(0xFF27272A),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = setting.label,
                color = White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = setting.key,
                color = TextDim,
                fontSize = 10.sp
            )
            Text(
                text = "当前：${setting.currentValue} | 期望：${setting.expectedValue}",
                color = TextDark,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        StatusBadge(
            text = if (setting.isCorrect) "正常" else "异常",
            ok = setting.isCorrect
        )
    }
}

// ========== 通用 Badge ==========

@Composable
private fun StatusBadge(text: String, ok: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (ok) GreenDarkBg else RedDarkBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (ok) Green else Red,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
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
            // 刷新按钮
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .then(if (!isLoading) Modifier else Modifier),
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = TextMuted
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "刷新检查",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 强制封锁按钮
            Button(
                onClick = onEnforce,
                enabled = !isLoading,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green,
                    contentColor = GreenDarkBg,
                    disabledContainerColor = Green.copy(alpha = 0.4f),
                    disabledContentColor = GreenDarkBg
                )
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "强制封锁",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Timestamp
        if (otaStatus != null) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            Text(
                text = "上次检查：${sdf.format(Date(otaStatus.lastCheckTime))}",
                color = TextDark,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ========== Tab Bar ==========

@Composable
private fun TabBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, BgBlack),
                    startY = 0f,
                    endY = 80f
                )
            )
            .padding(horizontal = 21.dp, vertical = 21.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(CardDark)
                .border(1.dp, Border, RoundedCornerShape(100.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // 防护 Tab - Active
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "防护",
                    color = Green,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 日志 Tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "日志",
                    color = TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
