plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// 采集侧 Android 绑定的宿主模块：单路 PCM 分发、AudioRecord 封装、振幅采样、
// AAC/M4A 分块编码、录音前台服务与命令总线。
android {
    namespace = "com.echofold.capture"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // 本模块的纯 JVM 测试不依赖 Android 资源，显式关闭资源打包以保持测试速度。
            isIncludeAndroidResources = false
            // 断言失败要给出完整堆栈；不启用 returnDefaultValues，避免 Android 桩的静默默认值掩盖问题。
            all { test ->
                test.useJUnitPlatform()
                test.testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // 共享契约模型只在 core 中定义，本模块与 asr_android 均复用同一份实现。
    api(project(":core"))

    testImplementation(libs.junit.jupiter)
    // Gradle 9 起 JUnit Platform launcher 需显式声明（与 core 模块一致）。
    testRuntimeOnly(libs.junit.platform.launcher)
}
