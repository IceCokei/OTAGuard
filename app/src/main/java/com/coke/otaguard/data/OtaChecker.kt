package com.coke.otaguard.data

import android.content.pm.PackageManager
import android.provider.Settings
import android.content.Context

data class PackageStatus(
    val packageName: String,
    val label: String,
    val description: String,
    val isDisabled: Boolean,
    val rawState: String
)

data class SettingStatus(
    val key: String,
    val label: String,
    val description: String,
    val currentValue: String,
    val expectedValue: String,
    val isCorrect: Boolean
)

data class OtaStatus(
    val packages: List<PackageStatus>,
    val settings: List<SettingStatus>,
    val hasRoot: Boolean,
    val moduleActive: Boolean,
    val overallSafe: Boolean,
    val lastCheckTime: Long
)

class OtaChecker(private val context: Context) {

    private val monitoredPackages = listOf(
        Triple("com.oplus.ota", "系统 OTA 更新", "主更新服务，负责检测/下载/安装系统更新"),
        Triple("com.oplus.cota", "组件静默更新", "后台静默推送小版本组件更新"),
        Triple("com.oplus.romupdate", "ROM 更新服务", "ROM 固件更新后台服务"),
        Triple("com.oplus.upgradeguide", "升级引导", "系统升级引导与提示界面"),
        Triple("com.google.android.configupdater", "Google 配置更新", "Google 服务框架配置自动更新"),
    )

    private val monitoredSettings = listOf(
        Triple("ota_disable_automatic_update", "禁止自动 OTA 更新", "1"),
        Triple("auto_download_network_type", "自动下载网络类型", "0"),
        Triple("can_update_at_night", "夜间自动更新", "0"),
    )

    fun check(): OtaStatus {
        AppLogger.info("开始检测系统状态...")

        val hasRoot = checkRoot()
        AppLogger.info("Root 权限: ${if (hasRoot) "可用" else "不可用"}")

        val moduleActive = com.coke.otaguard.hook.ModuleStatus.isActive()
        AppLogger.info("LSPosed 模块: ${if (moduleActive) "已激活" else "未激活"}")

        val packages = checkPackages()
        packages.forEach { pkg ->
            if (pkg.isDisabled) AppLogger.info("${pkg.label} (${pkg.packageName}): 已冻结")
            else AppLogger.warn("${pkg.label} (${pkg.packageName}): 运行中 ⚠")
        }

        val settings = checkSettings()
        settings.forEach { s ->
            if (s.isCorrect) AppLogger.info("${s.label}: 当前=${s.currentValue} ✓")
            else AppLogger.warn("${s.label}: 当前=${s.currentValue}, 期望=${s.expectedValue} ✗")
        }

        val overallSafe = packages.all { it.isDisabled } && settings.all { it.isCorrect }
        if (overallSafe) AppLogger.info("检测完成: 所有防护正常")
        else AppLogger.warn("检测完成: 存在未封锁的更新通道")

        return OtaStatus(
            packages = packages,
            settings = settings,
            hasRoot = hasRoot,
            moduleActive = moduleActive,
            overallSafe = overallSafe,
            lastCheckTime = System.currentTimeMillis()
        )
    }

    private fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val result = process.inputStream.bufferedReader().readText()
            process.waitFor()
            result.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    private fun checkPackages(): List<PackageStatus> {
        return monitoredPackages.map { (pkg, label, desc) ->
            val (isDisabled, rawState) = getPackageState(pkg)
            PackageStatus(
                packageName = pkg,
                label = label,
                description = desc,
                isDisabled = isDisabled,
                rawState = rawState
            )
        }
    }

    private fun getPackageState(packageName: String): Pair<Boolean, String> {
        // 优先使用 root 精确查询
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "pm dump $packageName | grep -m1 'pkgFlags\\|enabled='")
            )
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (output.contains("enabled=")) {
                val enabledMatch = Regex("enabled=(\\d)").find(output)
                val state = enabledMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1
                // 0=default(enabled), 1=enabled, 2=disabled, 3=disabled-user, 4=disabled-until-used
                return when (state) {
                    2, 3, 4 -> Pair(true, "disabled (state=$state)")
                    else -> Pair(false, "enabled (state=$state)")
                }
            }
        } catch (_: Exception) {}

        // 回退：使用 pm list packages -d 查询
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "pm list packages -d")
            )
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            val disabled = output.contains("package:$packageName")
            return Pair(disabled, if (disabled) "disabled-user" else "enabled")
        } catch (_: Exception) {}

        // 最后回退：PackageManager API
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            val enabled = info.enabled
            Pair(!enabled, if (enabled) "enabled (PM)" else "disabled (PM)")
        } catch (e: PackageManager.NameNotFoundException) {
            Pair(true, "not-found")
        }
    }

    private fun checkSettings(): List<SettingStatus> {
        return monitoredSettings.map { (key, label, expected) ->
            val value = getGlobalSetting(key)
            SettingStatus(
                key = key,
                label = label,
                description = "settings get global $key",
                currentValue = value,
                expectedValue = expected,
                isCorrect = value == expected
            )
        }
    }

    private fun getGlobalSetting(key: String): String {
        // 优先 root 方式
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "settings get global $key")
            )
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (result.isNotEmpty() && result != "null") return result
        } catch (_: Exception) {}

        // 回退：ContentResolver
        return try {
            Settings.Global.getString(context.contentResolver, key) ?: "null"
        } catch (_: Exception) {
            "error"
        }
    }

    fun enforceAll(): List<String> {
        AppLogger.info("执行强制封锁...")
        val results = mutableListOf<String>()

        monitoredPackages.forEach { (pkg, label, _) ->
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "pm disable $pkg")
                )
                val output = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                results.add("$label: $output")
                AppLogger.info("冻结 $label: $output")
            } catch (e: Exception) {
                results.add("$label: 失败 - ${e.message}")
                AppLogger.error("冻结 $label 失败: ${e.message}")
            }
        }

        monitoredSettings.forEach { (key, label, expected) ->
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "settings put global $key $expected")
                )
                process.waitFor()
                results.add("$label: 已设为 $expected")
                AppLogger.info("设置 $label = $expected")
            } catch (e: Exception) {
                results.add("$label: 失败 - ${e.message}")
                AppLogger.error("设置 $label 失败: ${e.message}")
            }
        }

        AppLogger.info("强制封锁执行完成")
        return results
    }
}
