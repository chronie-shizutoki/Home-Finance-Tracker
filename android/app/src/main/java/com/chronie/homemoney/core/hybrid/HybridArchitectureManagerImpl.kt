package com.chronie.homemoney.core.hybrid

import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridArchitectureManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HybridArchitectureManager {

    companion object {
        private const val BASE_URL = "http://192.168.0.197:3010"

        // 功能实现配置：由开发者控制，用户无法修改
        // 当某个功能完成原生迁移后，在这里修改为 NATIVE
        private val FEATURE_IMPLEMENTATIONS = mapOf(
            Feature.EXPENSES to ImplementationType.WEBVIEW,
            Feature.DEBTS to ImplementationType.WEBVIEW,
            Feature.DONATIONS to ImplementationType.WEBVIEW,
            Feature.CHARTS to ImplementationType.WEBVIEW,
            Feature.MEMBERSHIP to ImplementationType.WEBVIEW,
            Feature.MINIAPPS to ImplementationType.WEBVIEW
        )
    }

    override fun isFeatureNative(feature: Feature): Boolean {
        return getFeatureImplementation(feature) == ImplementationType.NATIVE
    }

    override fun getFeatureImplementation(feature: Feature): ImplementationType {
        return FEATURE_IMPLEMENTATIONS[feature] ?: ImplementationType.WEBVIEW
    }

    override fun launchFeature(context: Context, feature: Feature, params: Bundle?): LaunchResult {
        val implType = getFeatureImplementation(feature)

        return when (implType) {
            ImplementationType.NATIVE -> {
                LaunchResult.NativeRoute(
                    route = feature.toRoute(),
                    params = params
                )
            }

            ImplementationType.WEBVIEW -> {
                val url = getWebViewUrl(feature)
                LaunchResult.WebViewUrl(url)
            }

            ImplementationType.HYBRID -> {
                // 混合模式：根据具体情况决定
                // 目前默认使用 WebView
                val url = getWebViewUrl(feature)
                LaunchResult.WebViewUrl(url)
            }
        }
    }

    override fun getMigrationProgress(): Int {
        val total = FEATURE_IMPLEMENTATIONS.size
        if (total == 0) return 0

        val nativeCount = FEATURE_IMPLEMENTATIONS.values.count { it == ImplementationType.NATIVE }
        return (nativeCount * 100) / total
    }

    private fun getWebViewUrl(feature: Feature): String {
        return when (feature) {
            Feature.EXPENSES -> "$BASE_URL/"
            Feature.DEBTS -> "$BASE_URL/debts"
            Feature.DONATIONS -> "$BASE_URL/donation"
            Feature.CHARTS -> "$BASE_URL/charts"
            Feature.MEMBERSHIP -> "$BASE_URL/membership"
            Feature.MINIAPPS -> "$BASE_URL/" // 小程序在主页
        }
    }
}
