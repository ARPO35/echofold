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

dependencies {
    implementation(project(":capture_android"))
}
