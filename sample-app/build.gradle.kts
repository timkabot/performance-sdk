plugins {
    alias(commonLibs.plugins.android.application)
    alias(commonLibs.plugins.kotlin.android)
    alias(commonLibs.plugins.compose)
}

android {
    namespace = "io.github.timkabot.performance.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.timkabot.performance.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":performance-sdk"))

    implementation(platform(commonLibs.compose.bom))
    implementation("androidx.activity:activity-compose:${commonLibs.versions.androidxActivity.get()}")
    implementation(commonLibs.compose.foundation)
    implementation(commonLibs.compose.icons.extended)
    implementation(commonLibs.compose.material3)
    implementation("androidx.navigation:navigation-compose:2.9.8")
}
