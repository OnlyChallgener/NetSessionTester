plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)

    androidTarget()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        androidMain.dependencies {
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
        }
        desktopMain.dependencies {
            implementation(compose.desktop.common)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
        }
    }
}

android {
    namespace = "com.demonv.netsessiontester.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
}
