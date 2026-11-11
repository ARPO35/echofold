plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// ASR 侧 Android 绑定：模型清单信任、下载安装、租约管理与 sherpa-onnx 引擎适配。
android {
    namespace = "com.echofold.asr"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core"))
    // 清单/索引/租约使用 kotlinx JSON 的树模型做严格白名单解析；不需要 serialization 编译器插件。
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Android library 的 JVM 单测默认走 JUnit4 探测，这里统一切到 JUnit 5（与 core 一致）。
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
