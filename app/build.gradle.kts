plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.demonv.netsessiontester"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.demonv.netsessiontester"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
