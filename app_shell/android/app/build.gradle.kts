import java.security.MessageDigest
plugins {
    id("com.android.application")
    // Flutter Gradle 插件必须最后应用。
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    // Flutter 容器包名，与共享契约一致；原生模块分别位于 com.echofold.capture / com.echofold.asr。
    namespace = "com.echofold.app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.echofold.app"
        // 与 core/capture_android/asr_android 对齐：Android 10（API 29）起。
        minSdk = 29
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // 首版尚未配置发布签名，调试构建沿用 debug keystore。
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

val sherpaAar = file("libs/sherpa-onnx-1.13.6.aar")
check(sherpaAar.isFile) {
    "缺少 sherpa-onnx 1.13.6 AAR；请在仓库根目录运行：bash scripts/fetch-sherpa-onnx-aar.sh"
}
val sherpaSha256 = sherpaAar.inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var count = input.read(buffer)
    while (count != -1) {
        digest.update(buffer, 0, count)
        count = input.read(buffer)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}
check(sherpaSha256 == "0012d9a28f15bd6fb966b62b70a75da3990512fdccce28b83098248ce4be1698") {
    "sherpa-onnx AAR SHA-256 不匹配；请检查本地文件后运行：bash scripts/fetch-sherpa-onnx-aar.sh"
}

dependencies {
    implementation(project(":capture_android"))
    implementation(project(":asr_android"))
    implementation(files("libs/sherpa-onnx-1.13.6.aar"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// 与 core/asr_android 一致：单元测试统一走 JUnit 5。
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
