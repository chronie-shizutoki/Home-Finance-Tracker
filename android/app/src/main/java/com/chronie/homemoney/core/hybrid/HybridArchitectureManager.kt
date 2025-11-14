package com.chronie.homemoney.core.hybrid

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.flow.StateFlow

/**
 * 混合架构管理器接口
 * 管理原生功能和 WebView 功能的切换
 * 注意：功能实现类型由开发者在代码中配置，用户无法修改
 */
interface HybridArchitectureManager {
    /**
     * 检查某个功能是否已迁移到原生实现
     */
    fun isFeatureNative(feature: Feature): Boolean

    /**
     * 获取功能的实现类型
     */
    fun getFeatureImplementation(feature: Feature): ImplementationType

    /**
     * 启动功能（自动选择原生或 WebView）
     * @param context Android Context
     * @param feature 要启动的功能
     * @param params 传递给功能的参数
     * @return 导航路由或 WebView URL
     */
    fun launchFeature(context: Context, feature: Feature, params: Bundle? = null): LaunchResult

    /**
     * 获取迁移进度百分比（仅用于开发监控）
     */
    fun getMigrationProgress(): Int
}

/**
 * 功能启动结果
 */
sealed class LaunchResult {
    /**
     * 使用原生导航
     */
    data class NativeRoute(val route: String, val params: Bundle? = null) : LaunchResult()

    /**
     * 使用 WebView 加载
     */
    data class WebViewUrl(val url: String) : LaunchResult()

    /**
     * 功能未启用
     */
    data class Disabled(val reason: String) : LaunchResult()
}
