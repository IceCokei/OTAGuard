package com.coke.otaguard.data

import android.content.pm.PackageManager
import android.provider.Settings
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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

    companion object {
        private const val CMD_TIMEOUT = 10L // seconds
        private const val CACHE_KEY = "cached_status"
    }

    private val cachePrefs by lazy {
        context.getSharedPreferences("otaguard_cache", Context.MODE_PRIVATE)
    }

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

    private fun execRoot(vararg args: String): String? {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", *args))
            val result = process.inputStream.bufferedReader().use { it.readText().trim() }
            if (!process.waitFor(CMD_TIMEOUT, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            result.ifEmpty { null }
        } catch (_: Exception) {
            null
        } finally {
            process?.destroyForcibly()
        }
    }

    private fun checkRoot(): Boolean {
        val result = execRoot("id") ?: return false
        return result.contains("uid=0")
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
        val dumpOutput = execRoot("pm dump $packageName | grep -m1 'pkgFlags\\|enabled='")
        if (dumpOutput != null && dumpOutput.contains("enabled=")) {
            val enabledMatch = Regex("enabled=(\\d)").find(dumpOutput)
            val state = enabledMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1
            // 0=default(enabled), 1=enabled, 2=disabled, 3=disabled-user, 4=disabled-until-used
            return when (state) {
                2, 3, 4 -> Pair(true, "disabled (state=$state)")
                else -> Pair(false, "enabled (state=$state)")
            }
        }

        // 回退：使用 pm list packages -d 查询
        val listOutput = execRoot("pm list packages -d")
        if (listOutput != null) {
            val disabled = listOutput.contains("package:$packageName")
            return Pair(disabled, if (disabled) "disabled-user" else "enabled")
        }

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
        val result = execRoot("settings get global $key")
        if (result != null && result != "null") return result

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
            val output = execRoot("pm disable $pkg")
            if (output != null) {
                results.add("$label: $output")
                AppLogger.info("冻结 $label: $output")
            } else {
                results.add("$label: 失败")
                AppLogger.error("冻结 $label 失败")
            }
        }

        monitoredSettings.forEach { (key, label, expected) ->
            val output = execRoot("settings put global $key $expected")
            if (output != null || execRoot("settings get global $key") == expected) {
                results.add("$label: 已设为 $expected")
                AppLogger.info("设置 $label = $expected")
            } else {
                results.add("$label: 失败")
                AppLogger.error("设置 $label 失败")
            }
        }

        AppLogger.info("强制封锁执行完成")
        return results
    }

    fun saveCache(status: OtaStatus) {
        val json = JSONObject().apply {
            put("hasRoot", status.hasRoot)
            put("moduleActive", status.moduleActive)
            put("overallSafe", status.overallSafe)
            put("lastCheckTime", status.lastCheckTime)
            put("packages", JSONArray().apply {
                status.packages.forEach { pkg ->
                    put(JSONObject().apply {
                        put("packageName", pkg.packageName)
                        put("label", pkg.label)
                        put("description", pkg.description)
                        put("isDisabled", pkg.isDisabled)
                        put("rawState", pkg.rawState)
                    })
                }
            })
            put("settings", JSONArray().apply {
                status.settings.forEach { s ->
                    put(JSONObject().apply {
                        put("key", s.key)
                        put("label", s.label)
                        put("description", s.description)
                        put("currentValue", s.currentValue)
                        put("expectedValue", s.expectedValue)
                        put("isCorrect", s.isCorrect)
                    })
                }
            })
        }
        cachePrefs.edit().putString(CACHE_KEY, json.toString()).apply()
    }

    fun loadCache(): OtaStatus? {
        val jsonStr = cachePrefs.getString(CACHE_KEY, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val packages = mutableListOf<PackageStatus>()
            val pkgArray = json.getJSONArray("packages")
            for (i in 0 until pkgArray.length()) {
                val obj = pkgArray.getJSONObject(i)
                packages.add(PackageStatus(
                    packageName = obj.getString("packageName"),
                    label = obj.getString("label"),
                    description = obj.getString("description"),
                    isDisabled = obj.getBoolean("isDisabled"),
                    rawState = obj.getString("rawState")
                ))
            }
            val settings = mutableListOf<SettingStatus>()
            val settingsArray = json.getJSONArray("settings")
            for (i in 0 until settingsArray.length()) {
                val obj = settingsArray.getJSONObject(i)
                settings.add(SettingStatus(
                    key = obj.getString("key"),
                    label = obj.getString("label"),
                    description = obj.getString("description"),
                    currentValue = obj.getString("currentValue"),
                    expectedValue = obj.getString("expectedValue"),
                    isCorrect = obj.getBoolean("isCorrect")
                ))
            }
            OtaStatus(
                packages = packages,
                settings = settings,
                hasRoot = json.getBoolean("hasRoot"),
                moduleActive = json.getBoolean("moduleActive"),
                overallSafe = json.getBoolean("overallSafe"),
                lastCheckTime = json.getLong("lastCheckTime")
            )
        } catch (_: Exception) {
            null
        }
    }
}
