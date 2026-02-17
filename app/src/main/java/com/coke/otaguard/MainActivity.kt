package com.coke.otaguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.coke.otaguard.data.AppLogger
import com.coke.otaguard.data.OtaChecker
import com.coke.otaguard.data.OtaStatus
import com.coke.otaguard.ui.screens.HomeScreen
import com.coke.otaguard.ui.theme.OTAGuardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var checker: OtaChecker

    private val prefs by lazy {
        getSharedPreferences("otaguard_settings", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checker = OtaChecker(applicationContext)

        AppLogger.info("OTA Guard 启动")
        AppLogger.info("设备: ${android.os.Build.DEVICE} / ${android.os.Build.MODEL}")
        AppLogger.info("系统: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")

        setContent {
            var isDark by remember { mutableStateOf(prefs.getBoolean("is_dark", true)) }
            var otaStatus by remember { mutableStateOf<OtaStatus?>(null) }
            var isLoading by remember { mutableStateOf(false) }
            var logs by remember { mutableStateOf(AppLogger.logs) }
            val scope = rememberCoroutineScope()

            AppLogger.setListener { logs = AppLogger.logs }

            LaunchedEffect(Unit) {
                isLoading = true
                otaStatus = withContext(Dispatchers.IO) { checker.check() }
                isLoading = false
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
                            otaStatus = withContext(Dispatchers.IO) { checker.check() }
                            isLoading = false
                        }
                    },
                    onEnforce = {
                        scope.launch {
                            isLoading = true
                            withContext(Dispatchers.IO) { checker.enforceAll() }
                            otaStatus = withContext(Dispatchers.IO) { checker.check() }
                            isLoading = false
                        }
                    },
                    onToggleTheme = {
                        isDark = !isDark
                        prefs.edit().putBoolean("is_dark", isDark).apply()
                    }
                )
            }
        }
    }
}
