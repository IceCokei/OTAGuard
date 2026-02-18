package com.coke.otaguard.data

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class CloudCategory(val label: String) {
    TELEMETRY("遥测与统计"),
    CLOUD_SERVICE("云服务"),
    BEHAVIOR("行为分析"),
    DATA_REPORT("数据上报"),
    REMOTE_CONTROL("远程控制")
}

data class CloudPackageStatus(
    val packageName: String,
    val label: String,
    val description: String,
    val category: CloudCategory,
    val isDisabled: Boolean,
    val safeToDisable: Boolean,
    val rawState: String
)

data class CloudControlStatus(
    val packages: List<CloudPackageStatus>,
    val hasRoot: Boolean,
    val lastCheckTime: Long
)

class CloudControlChecker(private val context: Context) {

    companion object {
        private const val CMD_TIMEOUT = 15L
        private const val CACHE_KEY = "cached_cloud_status_v2"

        private data class KnownPackage(
            val pkg: String,
            val label: String,
            val category: CloudCategory,
            val desc: String,
            val safe: Boolean  // true = 可安全禁用
        )

        private val KNOWN_PACKAGES = listOf(
            // ===== 遥测与统计（全部可安全禁用） =====
            KnownPackage("com.oplus.statistics", "OPLUS 统计服务", CloudCategory.TELEMETRY, "采集使用数据上报，可安全禁用", true),
            KnownPackage("com.oplus.statistics.rom", "ROM 统计服务", CloudCategory.TELEMETRY, "ROM 层使用统计上报，可安全禁用", true),
            KnownPackage("com.oplus.crashbox", "崩溃收集服务", CloudCategory.TELEMETRY, "崩溃日志上报，可安全禁用", true),
            KnownPackage("com.oplus.qualityprotect", "质量保护服务", CloudCategory.TELEMETRY, "质量数据采集，可安全禁用", true),
            KnownPackage("com.oplus.onetrace", "OneTrace 追踪", CloudCategory.TELEMETRY, "系统运行追踪上报，可安全禁用", true),
            KnownPackage("com.coloros.operationManual", "运营手册", CloudCategory.TELEMETRY, "运营推送内容，可安全禁用", true),
            KnownPackage("com.coloros.bootreg", "开机注册服务", CloudCategory.TELEMETRY, "开机时上报设备信息，可安全禁用", true),
            KnownPackage("com.coloros.activation", "激活上报服务", CloudCategory.TELEMETRY, "设备激活数据上报，可安全禁用", true),
            KnownPackage("com.oplus.acc.gac", "全局分析采集", CloudCategory.TELEMETRY, "GAC 全局数据采集器，可安全禁用", true),
            KnownPackage("com.oplus.powermonitor", "功耗监控上报", CloudCategory.TELEMETRY, "电池功耗数据采集上报，可安全禁用", true),
            KnownPackage("com.oplus.trafficmonitor", "流量监控上报", CloudCategory.TELEMETRY, "网络流量使用数据上报，可安全禁用", true),
            // ===== 云服务 =====
            KnownPackage("com.heytap.cloud", "欢太云服务", CloudCategory.CLOUD_SERVICE, "云备份同步，禁用后无法云备份", false),
            KnownPackage("com.heytap.openid", "欢太账号服务", CloudCategory.CLOUD_SERVICE, "欢太账号登录，禁用影响欢太生态", false),
            KnownPackage("com.oplus.ocloud", "OPLUS 云同步", CloudCategory.CLOUD_SERVICE, "数据云同步，禁用后无法同步", false),
            KnownPackage("com.heytap.usercenter", "欢太用户中心", CloudCategory.CLOUD_SERVICE, "用户账号管理，禁用影响欢太登录", false),
            KnownPackage("com.oplus.account", "OPLUS 账号服务", CloudCategory.CLOUD_SERVICE, "OPLUS 账号登录体系，禁用影响账号功能", false),
            KnownPackage("com.heytap.market", "欢太应用商店", CloudCategory.CLOUD_SERVICE, "内置应用商店，可用其他商店替代", true),
            KnownPackage("com.heytap.browser", "欢太浏览器", CloudCategory.CLOUD_SERVICE, "内置浏览器，上报浏览数据，可安全禁用", true),
            KnownPackage("com.heytap.vip", "欢太会员服务", CloudCategory.CLOUD_SERVICE, "会员体系与营销推送，可安全禁用", true),
            KnownPackage("com.heytap.pictorial", "杂志锁屏", CloudCategory.CLOUD_SERVICE, "锁屏壁纸云端下载，可安全禁用", true),
            KnownPackage("com.heytap.quicksearchbox", "快搜服务", CloudCategory.CLOUD_SERVICE, "搜索查询上传云端，可安全禁用", true),
            KnownPackage("com.heytap.speechassist", "语音助手", CloudCategory.CLOUD_SERVICE, "语音数据上传识别，禁用影响语音功能", false),
            KnownPackage("com.heytap.mydevices", "我的设备", CloudCategory.CLOUD_SERVICE, "设备信息同步管理，可安全禁用", true),
            KnownPackage("com.heytap.accessory", "配件中心", CloudCategory.CLOUD_SERVICE, "智能配件云端管理，可安全禁用", true),
            KnownPackage("com.heytap.colorfulengine", "多彩引擎", CloudCategory.CLOUD_SERVICE, "主题资源云端下载，可安全禁用", true),
            KnownPackage("com.oplus.themestore", "主题商店", CloudCategory.CLOUD_SERVICE, "主题/壁纸下载，可安全禁用", true),
            // ===== 行为分析 =====
            KnownPackage("com.oplus.deepthinker", "深度思考引擎", CloudCategory.BEHAVIOR, "AI 行为预测，禁用可能影响智能功能", false),
            KnownPackage("com.oplus.obrain", "O-Brain AI 引擎", CloudCategory.BEHAVIOR, "系统级 AI 决策引擎，禁用影响智能省电", false),
            KnownPackage("com.oplus.appdetail", "应用详情分析", CloudCategory.BEHAVIOR, "分析应用使用习惯，可安全禁用", true),
            KnownPackage("com.oplus.uxdesign", "UX 行为采集", CloudCategory.BEHAVIOR, "用户体验数据采集，可安全禁用", true),
            KnownPackage("com.oplus.personalassist", "个人助理服务", CloudCategory.BEHAVIOR, "个性化推荐引擎，可安全禁用", true),
            KnownPackage("com.oplus.appsense", "应用感知服务", CloudCategory.BEHAVIOR, "应用行为感知分析，可安全禁用", true),
            KnownPackage("com.oplus.metis", "Metis 决策引擎", CloudCategory.BEHAVIOR, "系统行为决策分析，可安全禁用", true),
            KnownPackage("com.oplus.smartengine", "智能引擎", CloudCategory.BEHAVIOR, "智能调度行为分析，禁用影响智能优化", false),
            KnownPackage("com.oplus.travelengine", "出行引擎", CloudCategory.BEHAVIOR, "出行习惯与位置分析，可安全禁用", true),
            KnownPackage("com.oplus.atlas", "Atlas 数据图谱", CloudCategory.BEHAVIOR, "用户行为画像图谱，可安全禁用", true),
            // ===== 数据上报（绝大部分可安全禁用） =====
            KnownPackage("com.oplus.logkit", "日志采集套件", CloudCategory.DATA_REPORT, "系统日志上传，可安全禁用", true),
            KnownPackage("com.coloros.athena", "Athena 数据引擎", CloudCategory.DATA_REPORT, "ColorOS 数据分析平台，可安全禁用", true),
            KnownPackage("com.oplus.athena", "OPLUS Athena", CloudCategory.DATA_REPORT, "OPLUS 数据分析平台，可安全禁用", true),
            KnownPackage("com.oplus.sau", "系统分析上报", CloudCategory.DATA_REPORT, "系统使用分析，可安全禁用", true),
            KnownPackage("com.oplus.sauhelper", "SAU 上报助手", CloudCategory.DATA_REPORT, "辅助 SAU 数据上报，可安全禁用", true),
            KnownPackage("com.oplus.dmp", "数据管理平台", CloudCategory.DATA_REPORT, "DMP 用户画像数据，可安全禁用", true),
            KnownPackage("com.oplus.postmanservice", "消息投递服务", CloudCategory.DATA_REPORT, "后台数据投递通道，可安全禁用", true),
            KnownPackage("com.heytap.htms", "欢太 HTTP 消息", CloudCategory.DATA_REPORT, "欢太消息上报通道，可安全禁用", true),
            KnownPackage("com.heytap.tas", "欢太分析服务", CloudCategory.DATA_REPORT, "欢太数据分析采集，可安全禁用", true),
            KnownPackage("com.oplus.networksense", "网络感知服务", CloudCategory.DATA_REPORT, "网络状态采集上报，可安全禁用", true),
            KnownPackage("com.oplus.nwestimate", "网络质量评估", CloudCategory.DATA_REPORT, "网络质量数据上报，可安全禁用", true),
            KnownPackage("com.oplus.cell.map", "基站地图服务", CloudCategory.DATA_REPORT, "基站位置数据采集，可安全禁用", true),
            KnownPackage("com.oplus.cellularqoe", "蜂窝质量上报", CloudCategory.DATA_REPORT, "蜂窝网络质量上报，可安全禁用", true),
            KnownPackage("com.oplus.tai.wifiqoe", "WiFi 质量评估", CloudCategory.DATA_REPORT, "WiFi 使用质量上报，可安全禁用", true),
            KnownPackage("com.oplus.tai.borderpresearch", "网络边界预研", CloudCategory.DATA_REPORT, "网络切换数据上报，可安全禁用", true),
            KnownPackage("com.oplus.sense.netprediction", "网络预测服务", CloudCategory.DATA_REPORT, "网络使用预测上报，可安全禁用", true),
            KnownPackage("com.oplus.sense.netscore", "网络评分服务", CloudCategory.DATA_REPORT, "网络质量评分上报，可安全禁用", true),
            KnownPackage("com.oplus.nhs", "网络健康服务", CloudCategory.DATA_REPORT, "网络健康数据采集，可安全禁用", true),
            KnownPackage("com.oplus.nas", "网络分析服务", CloudCategory.DATA_REPORT, "NAS 网络数据分析，可安全禁用", true),
            KnownPackage("com.oplus.subsys", "子系统分析", CloudCategory.DATA_REPORT, "系统子模块数据上报，可安全禁用", true),
            KnownPackage("com.oplus.matrix", "Matrix 矩阵服务", CloudCategory.DATA_REPORT, "性能矩阵数据上报，可安全禁用", true),
            KnownPackage("com.oplus.olc", "在线配置服务", CloudCategory.DATA_REPORT, "OLC 在线参数下发与上报，可安全禁用", true),
            KnownPackage("com.oplus.cosa", "COSA 配置架构", CloudCategory.DATA_REPORT, "运营商配置上报，禁用可能影响运营商适配", false),
            KnownPackage("com.oplus.pantanal.ums", "统一消息服务", CloudCategory.DATA_REPORT, "UMS 后台消息通道，可安全禁用", true),
            KnownPackage("com.oplus.vdc", "虚拟数据通道", CloudCategory.DATA_REPORT, "VDC 后台数据传输，可安全禁用", true),
            KnownPackage("com.oplus.beaconlink", "Beacon 蓝牙追踪", CloudCategory.DATA_REPORT, "蓝牙信标位置追踪，可安全禁用", true),
            KnownPackage("com.oplus.linker", "Linker 互联服务", CloudCategory.DATA_REPORT, "应用间数据互联通道，可安全禁用", true),
            // ===== 远程控制 =====
            KnownPackage("com.oplus.epona", "Epona 远程管理", CloudCategory.REMOTE_CONTROL, "远程配置下发，禁用阻止远程控制", true),
            KnownPackage("com.oplus.customize", "远程定制服务", CloudCategory.REMOTE_CONTROL, "远程定制推送，可安全禁用", true),
            KnownPackage("com.oplus.customize.coreapp", "定制核心应用", CloudCategory.REMOTE_CONTROL, "远程定制核心模块，可安全禁用", true),
            KnownPackage("com.oplus.remotecontrol", "远程控制服务", CloudCategory.REMOTE_CONTROL, "远程设备控制入口，可安全禁用", true),
            KnownPackage("com.coloros.remoteguardservice", "远程守护服务", CloudCategory.REMOTE_CONTROL, "远程设备守护管理，可安全禁用", true),
            KnownPackage("com.nearme.instant.platform", "快应用平台", CloudCategory.REMOTE_CONTROL, "快应用运行环境，禁用阻止免安装应用", true),
            KnownPackage("com.coloros.ocs.opencapabilityservice", "快应用引擎", CloudCategory.REMOTE_CONTROL, "快应用能力框架，禁用阻止快应用加载", true),
            KnownPackage("com.oplus.appplatform", "应用分发平台", CloudCategory.REMOTE_CONTROL, "后台应用推送分发，可安全禁用", true),
            KnownPackage("com.heytap.mcs", "欢太消息推送", CloudCategory.REMOTE_CONTROL, "欢太推送服务，禁用影响欢太通知", false),
            KnownPackage("com.heytap.opluscarlink", "车机互联服务", CloudCategory.REMOTE_CONTROL, "手机车机互联通道，不用车联可禁用", true),
            KnownPackage("com.oplus.ocar", "O-Car 车联网", CloudCategory.REMOTE_CONTROL, "车联网远程通信，不用车联可禁用", true),
            KnownPackage("com.oplus.owork", "O-Work 办公互联", CloudCategory.REMOTE_CONTROL, "企业办公远程管理，不用可禁用", true),
        )
    }

    private val cachePrefs by lazy {
        context.getSharedPreferences("otaguard_cloud_cache", Context.MODE_PRIVATE)
    }

    private val pm: PackageManager get() = context.packageManager

    fun check(): CloudControlStatus {
        AppLogger.info("[云控] 开始扫描云控组件...")

        val hasRoot = checkRoot()
        AppLogger.info("[云控] Root 权限: ${if (hasRoot) "可用" else "不可用"}")

        if (!hasRoot) {
            AppLogger.error("[云控] 无 Root 权限，无法扫描")
            return CloudControlStatus(emptyList(), false, System.currentTimeMillis())
        }

        // 获取禁用包列表（1 次 root 调用）
        AppLogger.info("[云控] 获取包状态...")
        val disabledPkgs = execRoot("pm list packages -d")?.lines()
            ?.filter { it.startsWith("package:") }
            ?.map { it.removePrefix("package:").trim() }
            ?.toSet() ?: emptySet()

        // PackageManager API 获取已安装包（无需 root，毫秒级）
        @Suppress("DEPRECATION")
        val installedPkgs = pm.getInstalledPackages(0).map { it.packageName }.toSet()

        // 匹配已知云控包
        val results = KNOWN_PACKAGES.mapNotNull { known ->
            if (known.pkg !in installedPkgs) return@mapNotNull null
            val isDisabled = known.pkg in disabledPkgs
            val state = if (isDisabled) "已禁用" else "运行中"
            AppLogger.info("[云控]   ${known.label}: $state")
            CloudPackageStatus(
                known.pkg, known.label, known.desc, known.category,
                isDisabled, known.safe, if (isDisabled) "disabled" else "enabled"
            )
        }

        val safeCount = results.count { it.safeToDisable }
        val disabledCount = results.count { it.isDisabled }
        AppLogger.info("[云控] 扫描完成: 共 ${results.size} 个云控组件，已禁用 $disabledCount 个，可安全禁用 $safeCount 个")

        return CloudControlStatus(results, hasRoot, System.currentTimeMillis())
    }

    fun disablePackage(pkg: String): Boolean {
        AppLogger.info("[云控] 禁用: $pkg")
        val output = execRoot("pm disable-user --user 0 $pkg")
        val success = output != null && (output.contains("disabled") || output.contains("new state"))
        if (success) AppLogger.info("[云控] 禁用成功: $pkg")
        else AppLogger.error("[云控] 禁用失败: $pkg")
        return success
    }

    fun enablePackage(pkg: String): Boolean {
        AppLogger.info("[云控] 恢复: $pkg")
        val output = execRoot("pm enable $pkg")
        val success = output != null && (output.contains("enabled") || output.contains("new state"))
        if (success) AppLogger.info("[云控] 恢复成功: $pkg")
        else AppLogger.error("[云控] 恢复失败: $pkg")
        return success
    }

    /** 一键禁用：只处理 safeToDisable=true 且未禁用的包 */
    fun disableAllSafe(): List<String> {
        AppLogger.info("[云控] 一键安全禁用...")
        val results = mutableListOf<String>()
        val lastStatus = loadCache() ?: return results

        lastStatus.packages
            .filter { !it.isDisabled && it.safeToDisable }
            .forEach { pkg ->
                val success = disablePackage(pkg.packageName)
                results.add("${pkg.label}: ${if (success) "已禁用" else "失败"}")
            }

        AppLogger.info("[云控] 安全禁用完成: ${results.size} 个")
        return results
    }

    /** Root 卸载（对当前用户移除，可通过 pm install-existing 恢复） */
    fun uninstallPackage(pkg: String): Boolean {
        AppLogger.info("[云控] 卸载: $pkg")
        val output = execRoot("pm uninstall -k --user 0 $pkg")
        val success = output != null && output.contains("Success")
        if (success) AppLogger.info("[云控] 卸载成功: $pkg")
        else AppLogger.error("[云控] 卸载失败: $pkg")
        return success
    }

    /** 一键卸载：只处理 safeToDisable=true 的包 */
    fun uninstallAllSafe(): List<String> {
        AppLogger.info("[云控] 一键安全卸载...")
        val results = mutableListOf<String>()
        val lastStatus = loadCache() ?: return results

        lastStatus.packages
            .filter { it.safeToDisable }
            .forEach { pkg ->
                val success = uninstallPackage(pkg.packageName)
                results.add("${pkg.label}: ${if (success) "已卸载" else "失败"}")
            }

        AppLogger.info("[云控] 安全卸载完成: ${results.size} 个")
        return results
    }

    fun saveCache(status: CloudControlStatus) {
        val json = JSONObject().apply {
            put("hasRoot", status.hasRoot)
            put("lastCheckTime", status.lastCheckTime)
            put("packages", JSONArray().apply {
                status.packages.forEach { pkg ->
                    put(JSONObject().apply {
                        put("packageName", pkg.packageName)
                        put("label", pkg.label)
                        put("description", pkg.description)
                        put("category", pkg.category.name)
                        put("isDisabled", pkg.isDisabled)
                        put("safeToDisable", pkg.safeToDisable)
                        put("rawState", pkg.rawState)
                    })
                }
            })
        }
        cachePrefs.edit().putString(CACHE_KEY, json.toString()).apply()
    }

    fun loadCache(): CloudControlStatus? {
        val jsonStr = cachePrefs.getString(CACHE_KEY, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val packages = mutableListOf<CloudPackageStatus>()
            val pkgArray = json.getJSONArray("packages")
            for (i in 0 until pkgArray.length()) {
                val obj = pkgArray.getJSONObject(i)
                packages.add(CloudPackageStatus(
                    packageName = obj.getString("packageName"),
                    label = obj.getString("label"),
                    description = obj.getString("description"),
                    category = try {
                        CloudCategory.valueOf(obj.getString("category"))
                    } catch (_: Exception) {
                        CloudCategory.TELEMETRY
                    },
                    isDisabled = obj.getBoolean("isDisabled"),
                    safeToDisable = obj.optBoolean("safeToDisable", false),
                    rawState = obj.getString("rawState")
                ))
            }
            CloudControlStatus(packages, json.getBoolean("hasRoot"), json.getLong("lastCheckTime"))
        } catch (_: Exception) {
            null
        }
    }

    private fun execRoot(vararg args: String): String? {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", *args))
            val result = process.inputStream.bufferedReader().use { it.readText().trim() }
            if (!process.waitFor(CMD_TIMEOUT, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                AppLogger.warn("[云控] 命令超时: ${args.joinToString(" ")}")
                return null
            }
            result.ifEmpty { null }
        } catch (e: Exception) {
            AppLogger.error("[云控] 命令失败: ${e.message}")
            null
        } finally {
            process?.destroyForcibly()
        }
    }

    private fun checkRoot(): Boolean {
        val result = execRoot("id") ?: return false
        return result.contains("uid=0")
    }
}
