package com.coke.otaguard.hook

/**
 * LSPosed 模块激活状态检测
 * 当模块被 LSPosed 加载时，isActive() 会被 hook 返回 true
 */
object ModuleStatus {

    private const val MODULE_VERSION = 1

    /**
     * 此方法的返回值会被 Xposed hook 替换
     * 未激活时返回 false，激活后返回 true
     */
    fun isActive(): Boolean = false

    fun getVersion(): Int = MODULE_VERSION
}
