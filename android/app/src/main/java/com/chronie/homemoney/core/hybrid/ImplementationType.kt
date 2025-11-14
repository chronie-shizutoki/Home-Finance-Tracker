package com.chronie.homemoney.core.hybrid

/**
 * 功能实现类型
 */
enum class ImplementationType {
    /**
     * 原生实现 - 使用 Kotlin/Compose 实现
     */
    NATIVE,

    /**
     * WebView 实现 - 使用现有的 Vue.js 网页
     */
    WEBVIEW,

    /**
     * 混合实现 - 部分功能使用原生，部分使用 WebView
     */
    HYBRID
}
