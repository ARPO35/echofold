plugins {
    `java-library`
    // 插件版本统一在根级 settings.gradle.kts 声明，模块只按 id 引用。
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// core 是纯 JVM 模块，禁止依赖 Android SDK：保证模型与协议可脱离设备直接单测。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // AsrEvent 的线上/日志格式由 kotlinx-serialization 生成；类型出现在公开签名中，故用 api。
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    // Gradle 9 起 JUnit Platform launcher 需显式声明。
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
