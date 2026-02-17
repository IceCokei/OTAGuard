package com.coke.otaguard.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        private const val TAG = "OTAGuard"

        // 要拦截的 OTA 相关包
        private val OTA_PACKAGES = setOf(
            "com.oplus.ota",
            "com.oplus.cota",
            "com.oplus.romupdate",
            "com.oplus.upgradeguide"
        )

        // 要保护的 settings global 键值
        private val PROTECTED_SETTINGS = mapOf(
            "ota_disable_automatic_update" to "1",
            "auto_download_network_type" to "0",
            "can_update_at_night" to "0"
        )
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        XposedBridge.log("[$TAG] initZygote: OTA Guard 已加载")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "com.coke.otaguard" -> hookSelf(lpparam)
            "android" -> hookSystemServer(lpparam)
            in OTA_PACKAGES -> killOtaPackage(lpparam)
        }
    }

    /**
     * Hook 自身 App - 让 ModuleStatus.isActive() 返回 true
     */
    private fun hookSelf(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.coke.otaguard.hook.ModuleStatus",
                lpparam.classLoader,
                "isActive",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
            XposedBridge.log("[$TAG] 模块自检 hook 成功")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] 模块自检 hook 失败: ${e.message}")
        }
    }

    /**
     * Hook system_server：拦截 Settings.Global 写入，阻止 OTA 设置被篡改
     */
    private fun hookSystemServer(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("[$TAG] hooking system_server")

        // Hook Settings.Global.putString - 拦截设置写入
        try {
            val settingsGlobal = XposedHelpers.findClass(
                "android.provider.Settings\$Global",
                lpparam.classLoader
            )

            // putString(ContentResolver, String, String)
            XposedHelpers.findAndHookMethod(
                settingsGlobal, "putString",
                "android.content.ContentResolver",
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String ?: return
                        val value = param.args[2] as? String
                        val expected = PROTECTED_SETTINGS[key] ?: return

                        if (value != expected) {
                            XposedBridge.log("[$TAG] 阻止修改: $key = $value (期望: $expected)")
                            param.args[2] = expected
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] Settings.Global.putString hooked")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hook putString 失败: ${e.message}")
        }

        // putInt(ContentResolver, String, int)
        try {
            val settingsGlobal = XposedHelpers.findClass(
                "android.provider.Settings\$Global",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                settingsGlobal, "putInt",
                "android.content.ContentResolver",
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String ?: return
                        val expected = PROTECTED_SETTINGS[key]?.toIntOrNull() ?: return
                        val value = param.args[2] as? Int ?: return

                        if (value != expected) {
                            XposedBridge.log("[$TAG] 阻止修改(int): $key = $value (期望: $expected)")
                            param.args[2] = expected
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] Settings.Global.putInt hooked")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hook putInt 失败: ${e.message}")
        }

        // Hook PackageManagerService - 阻止 OTA 包被重新启用
        hookPackageManager(lpparam)
    }

    /**
     * Hook PackageManagerService：阻止 OTA 包被偷偷启用
     */
    private fun hookPackageManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // setApplicationEnabledSetting - 拦截启用包的操作
            val pmsClass = XposedHelpers.findClass(
                "com.android.server.pm.PackageManagerService",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                pmsClass,
                "setApplicationEnabledSetting",
                String::class.java,           // packageName
                Int::class.javaPrimitiveType,  // newState
                Int::class.javaPrimitiveType,  // flags
                Int::class.javaPrimitiveType,  // userId
                String::class.java,            // callingPackage
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[0] as? String ?: return
                        val newState = param.args[1] as? Int ?: return

                        if (pkg in OTA_PACKAGES && newState == 0) {
                            // 0 = COMPONENT_ENABLED_STATE_DEFAULT (启用)
                            XposedBridge.log("[$TAG] 阻止启用 OTA 包: $pkg (state=$newState)")
                            param.result = null
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] PMS.setApplicationEnabledSetting hooked")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hook PMS 失败: ${e.message}")
            // Android 14+ 可能签名变了，尝试备用方案
            hookPmsCompat(lpparam)
        }
    }

    /**
     * 兼容 Android 14+ 的 PMS hook
     */
    private fun hookPmsCompat(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Android 14+ 重构了 PMS，尝试 PackageManagerShellCommand 或 IPackageManager
            val ipmClass = XposedHelpers.findClass(
                "android.content.pm.IPackageManager\$Stub\$Proxy",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                ipmClass,
                "setApplicationEnabledSetting",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[0] as? String ?: return
                        val newState = param.args[1] as? Int ?: return

                        if (pkg in OTA_PACKAGES && newState == 0) {
                            XposedBridge.log("[$TAG] [compat] 阻止启用 OTA 包: $pkg")
                            param.result = null
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] PMS compat hook 成功")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] PMS compat hook 也失败: ${e.message}")
        }
    }

    /**
     * 对 OTA 包本身进行 hook - 直接破坏其核心功能
     */
    private fun killOtaPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("[$TAG] 拦截 OTA 包启动: ${lpparam.packageName}")

        // Hook Application.onCreate - 直接杀死进程
        try {
            val appClass = XposedHelpers.findClass(
                "android.app.Application",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                appClass, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("[$TAG] 终止 OTA 进程: ${lpparam.packageName}")
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] kill OTA 包失败: ${e.message}")
        }

        // Hook 网络请求 - 阻止 OTA 检查
        try {
            val urlClass = XposedHelpers.findClass(
                "java.net.URL",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                urlClass, "openConnection",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val url = param.thisObject.toString()
                        if (url.contains("ota", ignoreCase = true) ||
                            url.contains("update", ignoreCase = true) ||
                            url.contains("upgrade", ignoreCase = true) ||
                            url.contains("rom", ignoreCase = true)
                        ) {
                            XposedBridge.log("[$TAG] 阻断 OTA 网络请求: $url")
                            param.throwable = java.io.IOException("OTA Guard: blocked")
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hook URL 失败: ${e.message}")
        }
    }
}
