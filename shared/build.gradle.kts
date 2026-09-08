import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
            isStatic = false
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        iosMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        androidMain.dependencies {
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
        }
        jvmMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.demonv.netsessiontester.desktop.MainKt"
        nativeDistributions {
            // Include the runtime modules used by Compose, NIO, DNS and platform integration.
            includeAllModules = true
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "NetSessionTester"
            packageVersion = "1.0.22"
            description = "NetSessionTester network diagnostics"
            vendor = "OnlyChallgener"
            macOS {
                bundleID = "com.demonv.netsessiontester"
                iconFile.set(project.file("../desktop/assets/icon.icns"))
            }
            windows {
                iconFile.set(project.file("../desktop/assets/icon.ico"))
                menuGroup = "NetSessionTester"
                upgradeUuid = "5d8f706b-4a9e-4cc8-a7e1-b8b6b4bb7f4a"
                perUserInstall = false
                dirChooser = true
            }
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
