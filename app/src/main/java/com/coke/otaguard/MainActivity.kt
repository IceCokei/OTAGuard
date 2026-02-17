package com.coke.otaguard

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.coke.otaguard.data.OtaChecker
import com.coke.otaguard.data.OtaStatus
import com.coke.otaguard.ui.screens.HomeScreen
import com.coke.otaguard.ui.theme.OTAGuardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var checker: OtaChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checker = OtaChecker(applicationContext)

        setContent {
            OTAGuardTheme {
                var otaStatus by remember { mutableStateOf<OtaStatus?>(null) }
                var isLoading by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                // 首次加载
                LaunchedEffect(Unit) {
                    isLoading = true
                    otaStatus = withContext(Dispatchers.IO) { checker.check() }
                    isLoading = false
                }

                HomeScreen(
                    otaStatus = otaStatus,
                    isLoading = isLoading,
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
                            val results = withContext(Dispatchers.IO) { checker.enforceAll() }
                            otaStatus = withContext(Dispatchers.IO) { checker.check() }
                            isLoading = false
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "执行完成：\n${results.joinToString("\n")}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }
        }
    }
}
