import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties().apply {
    val file = rootProject.file("signing.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}

fun signingValue(name: String): String? {
    return (System.getenv(name) ?: signingProperties.getProperty(name))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

val labprobeKeystorePath = signingValue("LABPROBE_KEYSTORE_PATH")
val hasLabprobeSigning = labprobeKeystorePath != null

android {
    namespace = "com.labprobe.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.labprobe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 55
        versionName = "0.9.15"
    }

    signingConfigs {
        if (hasLabprobeSigning) {
            create("labprobeUpload") {
                storeFile = rootProject.file(labprobeKeystorePath!!)
                storePassword = signingValue("LABPROBE_KEYSTORE_PASSWORD")
                    ?: error("Missing LABPROBE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("LABPROBE_KEY_ALIAS") ?: "labprobe"
                keyPassword = signingValue("LABPROBE_KEY_PASSWORD")
                    ?: signingValue("LABPROBE_KEYSTORE_PASSWORD")
                    ?: error("Missing LABPROBE_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasLabprobeSigning) {
                signingConfig = signingConfigs.getByName("labprobeUpload")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (hasLabprobeSigning) {
                signingConfig = signingConfigs.getByName("labprobeUpload")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            // GitHub 网页上传覆盖文件时，旧的 app/src/main/java 可能不会被删除。
            // 这里强制只编译当前工程使用的 Kotlin 源码目录，避免旧 TestForegroundService.kt 等遗留文件参与编译。
            java.setSrcDirs(listOf("src/main/kotlin"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.mwiede:jsch:0.2.21")
}
