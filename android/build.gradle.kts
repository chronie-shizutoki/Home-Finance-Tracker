plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.protobuf) apply false
}

subprojects {
    afterEvaluate {
        if (plugins.hasPlugin("com.android.library")) {
            the<com.android.build.api.dsl.LibraryExtension>().apply {
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_25
                    targetCompatibility = JavaVersion.VERSION_25
                }
            }
        }
        if (plugins.hasPlugin("com.android.application")) {
            the<com.android.build.api.dsl.ApplicationExtension>().apply {
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_25
                    targetCompatibility = JavaVersion.VERSION_25
                }
            }
        }
    }
}

tasks.register<Delete>("clean") {
    description = "Clean build directory"
    delete(rootProject.layout.buildDirectory)
}
