plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.demonv.netsessiontester"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.demonv.netsessiontester"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.5.0-react-kotlin"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
