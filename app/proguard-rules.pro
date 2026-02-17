# OTA Guard ProGuard Rules
-keepclassmembers class com.coke.otaguard.data.** { *; }

# Xposed hook 入口 - 不可混淆
-keep class com.coke.otaguard.hook.MainHook { *; }
-keep class com.coke.otaguard.hook.ModuleStatus { *; }

# Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
