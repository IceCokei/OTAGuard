package com.coke.otaguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.coke.otaguard.data.AppLogger
import com.coke.otaguard.data.CloudControlChecker
import com.coke.otaguard.data.CloudControlStatus
import com.coke.otaguard.data.OtaChecker
import com.coke.otaguard.data.OtaStatus
import com.coke.otaguard.hook.ModuleStatus
import com.coke.otaguard.ui.screens.HomeScreen
import com.coke.otaguard.ui.theme.OTAGuardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var checker: OtaChecker
    private lateinit var cloudChecker: CloudControlChecker

    private val prefs by lazy {
        getSharedPreferences("otaguard_settings", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checker = OtaChecker(applicationContext)
        cloudChecker = CloudControlChecker(applicationContext)

        AppLogger.info("OTA Guard 启动")
        AppLogger.info("设备: ${android.os.Build.DEVICE} / ${android.os.Build.MODEL}")
        AppLogger.info("系统: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")

        setContent {
            var isDark by remember { mutableStateOf(prefs.getBoolean("is_dark", true)) }
            var otaStatus by remember { mutableStateOf<OtaStatus?>(null) }
            var isLoading by remember { mutableStateOf(false) }
            var cloudStatus by remember { mutableStateOf<CloudControlStatus?>(null) }
            var isCloudLoading by remember { mutableStateOf(false) }
            var logs by remember { mutableStateOf(AppLogger.logs) }
            val scope = rememberCoroutineScope()

            AppLogger.setListener { logs = AppLogger.logs }

            LaunchedEffect(Unit) {
                val cloudCached = withContext(Dispatchers.IO) { cloudChecker.loadCache() }
                if (cloudCached != null) cloudStatus = cloudCached

                val cached = withContext(Dispatchers.IO) { checker.loadCache() }
                if (cached != null && cached.overallSafe && cached.hasRoot) {
                    // 缓存存在且上次检测全部正常
                    val moduleStillActive = ModuleStatus.isActive()
                    if (moduleStillActive == cached.moduleActive) {
                        // LSPosed 状态未变化，直接使用缓存
                        otaStatus = cached
                        AppLogger.info("系统状态正常，加载上次检测结果")
                    } else {
                        // LSPosed 状态变更，需要重新检测
                        AppLogger.warn("LSPosed 模块状态变更，重新检测...")
                        isLoading = true
                        val result = withContext(Dispatchers.IO) { checker.check() }
                        withContext(Dispatchers.IO) { checker.saveCache(result) }
                        otaStatus = result
                        isLoading = false
                    }
                } else {
                    // 无缓存或上次检测存在问题，执行完整扫描
                    isLoading = true
                    val result = withContext(Dispatchers.IO) { checker.check() }
                    withContext(Dispatchers.IO) { checker.saveCache(result) }
                    otaStatus = result
                    isLoading = false
                }
            }

            OTAGuardTheme(isDark = isDark) {
                HomeScreen(
                    otaStatus = otaStatus,
                    isLoading = isLoading,
                    logs = logs,
                    isDark = isDark,
                    onRefresh = {
                        scope.launch {
                            isLoading = true
                            val result = withContext(Dispatchers.IO) { checker.check() }
                            withContext(Dispatchers.IO) { checker.saveCache(result) }
                            otaStatus = result
                            isLoading = false
                        }
                    },
                    onEnforce = {
                        scope.launch {
                            isLoading = true
                            withContext(Dispatchers.IO) { checker.enforceAll() }
                            val result = withContext(Dispatchers.IO) { checker.check() }
                            withContext(Dispatchers.IO) { checker.saveCache(result) }
                            otaStatus = result
                            isLoading = false
                        }
                    },
                    onToggleTheme = {
                        isDark = !isDark
                        prefs.edit().putBoolean("is_dark", isDark).apply()
                    },
                    cloudStatus = cloudStatus,
                    isCloudLoading = isCloudLoading,
                    onCloudScan = {
                        scope.launch {
                            isCloudLoading = true
                            val result = withContext(Dispatchers.IO) { cloudChecker.check() }
                            withContext(Dispatchers.IO) { cloudChecker.saveCache(result) }
                            cloudStatus = result
                            isCloudLoading = false
                        }
                    },
                    onCloudDisableAll = {
                        scope.launch {
                            isCloudLoading = true
                            withContext(Dispatchers.IO) { cloudChecker.disableAllSafe() }
                            val result = withContext(Dispatchers.IO) { cloudChecker.check() }
                            withContext(Dispatchers.IO) { cloudChecker.saveCache(result) }
                            cloudStatus = result
                            isCloudLoading = false
                        }
                    },
                    onCloudUninstallAll = {
                        scope.launch {
                            isCloudLoading = true
                            withContext(Dispatchers.IO) { cloudChecker.uninstallAllSafe() }
                            val result = withContext(Dispatchers.IO) { cloudChecker.check() }
                            withContext(Dispatchers.IO) { cloudChecker.saveCache(result) }
                            cloudStatus = result
                            isCloudLoading = false
                        }
                    },
                    onCloudToggle = { pkg, disable ->
                        scope.launch {
                            isCloudLoading = true
                            withContext(Dispatchers.IO) {
                                if (disable) cloudChecker.disablePackage(pkg)
                                else cloudChecker.enablePackage(pkg)
                            }
                            val result = withContext(Dispatchers.IO) { cloudChecker.check() }
                            withContext(Dispatchers.IO) { cloudChecker.saveCache(result) }
                            cloudStatus = result
                            isCloudLoading = false
                        }
                    }
                )
            }
        }
    }
}
