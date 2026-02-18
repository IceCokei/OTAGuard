package com.coke.otaguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coke.otaguard.data.CloudCategory
import com.coke.otaguard.data.CloudControlStatus
import com.coke.otaguard.data.CloudPackageStatus
import com.coke.otaguard.ui.theme.LocalOtaColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CloudControlPage(
    cloudStatus: CloudControlStatus?,
    isLoading: Boolean,
    onScan: () -> Unit,
    onDisableAll: () -> Unit,
    onUninstallAll: () -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    val colors = LocalOtaColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 64.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 标题
        item {
            Text(
                text = "云控监控",
                color = colors.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 56.dp)
            )
        }

        // 统计卡片
        item {
            CloudStatsRow(cloudStatus)
        }

        // 分类区块
        if (cloudStatus != null && cloudStatus.packages.isNotEmpty()) {
            val grouped = cloudStatus.packages.groupBy { it.category }
            val categoryOrder = listOf(
                CloudCategory.TELEMETRY,
                CloudCategory.CLOUD_SERVICE,
                CloudCategory.BEHAVIOR,
                CloudCategory.DATA_REPORT,
                CloudCategory.REMOTE_CONTROL
            )

            categoryOrder.forEach { category ->
                val pkgs = grouped[category]
                if (pkgs != null && pkgs.isNotEmpty()) {
                    item(key = "header_${category.name}") {
                        CategoryHeader(category, pkgs.size)
                    }
                    item(key = "list_${category.name}") {
                        CategoryPackageList(pkgs, onToggle)
                    }
                }
            }
        } else if (cloudStatus == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "点击「扫描检测」开始扫描云控组件",
                        color = colors.textDim,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // 操作按钮
        item {
            CloudActionsSection(cloudStatus, isLoading, onScan, onDisableAll, onUninstallAll)
        }
    }
}

// ========== 统计卡片 ==========

@Composable
private fun CloudStatsRow(cloudStatus: CloudControlStatus?) {
    val colors = LocalOtaColors.current
    val total = cloudStatus?.packages?.size ?: 0
    val disabled = cloudStatus?.packages?.count { it.isDisabled } ?: 0
    val unsafe = cloudStatus?.packages?.count { !it.safeToDisable } ?: 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CloudMetricCard(
            value = "$disabled/$total",
            label = "已拦截",
            color = colors.green,
            bgColor = colors.greenBg,
            modifier = Modifier.weight(1f)
        )
        CloudMetricCard(
            value = "$unsafe",
            label = "需手动判断",
            color = colors.amber,
            bgColor = colors.amberBg,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CloudMetricCard(
    value: String,
    label: String,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalOtaColors.current
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            color = color,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ========== 分类标题 ==========

@Composable
private fun CategoryHeader(category: CloudCategory, count: Int) {
    val colors = LocalOtaColors.current
    val (icon, tint) = categoryIcon(category)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = category.label,
                color = colors.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.border)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("$count", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun categoryIcon(category: CloudCategory): Pair<ImageVector, Color> {
    val colors = LocalOtaColors.current
    return when (category) {
        CloudCategory.TELEMETRY -> Icons.Outlined.Analytics to colors.blue
        CloudCategory.CLOUD_SERVICE -> Icons.Outlined.Cloud to colors.blue
        CloudCategory.BEHAVIOR -> Icons.Outlined.Psychology to colors.amber
        CloudCategory.DATA_REPORT -> Icons.Outlined.Upload to colors.amber
        CloudCategory.REMOTE_CONTROL -> Icons.Outlined.SettingsRemote to colors.red
    }
}

// ========== 分类包列表 ==========

@Composable
private fun CategoryPackageList(
    packages: List<CloudPackageStatus>,
    onToggle: (String, Boolean) -> Unit
) {
    val colors = LocalOtaColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
    ) {
        packages.forEachIndexed { index, pkg ->
            CloudPackageRow(pkg, isLast = index == packages.lastIndex, onToggle = onToggle)
        }
    }
}

@Composable
private fun CloudPackageRow(
    pkg: CloudPackageStatus,
    isLast: Boolean,
    onToggle: (String, Boolean) -> Unit
) {
    val colors = LocalOtaColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isLast) Modifier.drawBehind {
                    drawLine(colors.border, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                } else Modifier
            )
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(pkg.label, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(pkg.packageName, color = colors.textDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(pkg.description, color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Switch(
            checked = pkg.isDisabled,
            onCheckedChange = { checked -> onToggle(pkg.packageName, checked) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.green,
                checkedTrackColor = colors.greenBg,
                uncheckedThumbColor = colors.red,
                uncheckedTrackColor = colors.redBg
            )
        )
    }
}

// ========== 操作按钮 ==========

@Composable
private fun CloudActionsSection(
    cloudStatus: CloudControlStatus?,
    isLoading: Boolean,
    onScan: () -> Unit,
    onDisableAll: () -> Unit,
    onUninstallAll: () -> Unit
) {
    val colors = LocalOtaColors.current
    val hasSafeToDisable = cloudStatus?.packages?.any { !it.isDisabled && it.safeToDisable } == true
    val hasSafeToUninstall = cloudStatus?.packages?.any { it.safeToDisable } == true

    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 第一行：扫描 + 一键禁用
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onScan,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = colors.text,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = colors.textDim
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.textMuted
                        )
                    } else {
                        Icon(Icons.Outlined.Radar, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("扫描检测", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = onDisableAll,
                enabled = !isLoading && hasSafeToDisable,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.green,
                    contentColor = colors.greenBg,
                    disabledContainerColor = colors.green.copy(alpha = 0.4f),
                    disabledContentColor = colors.greenBg
                )
            ) {
                Icon(Icons.Outlined.CloudOff, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("一键禁用", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 第二行：一键卸载（红色警告）
        Button(
            onClick = onUninstallAll,
            enabled = !isLoading && hasSafeToUninstall,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.red,
                contentColor = Color.White,
                disabledContainerColor = colors.red.copy(alpha = 0.4f),
                disabledContentColor = Color.White.copy(alpha = 0.6f)
            )
        ) {
            Icon(Icons.Outlined.DeleteForever, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("一键卸载（仅安全项，可恢复）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        // 上次扫描时间
        if (cloudStatus != null) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            Text(
                text = "上次扫描：${sdf.format(Date(cloudStatus.lastCheckTime))}",
                color = colors.textDark,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}
