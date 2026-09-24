import org.gradle.api.tasks.testing.Test

plugins {
    alias(commonLibs.plugins.android.library)
    alias(commonLibs.plugins.kotlin.android)
    alias(commonLibs.plugins.compose)
    id("maven-publish")
}

group = "io.github.timkabot.performance"
version = providers.gradleProperty("performanceSdkVersion")
    .orElse(providers.gradleProperty("PERFORMANCE_SDK_VERSION"))
    .orElse("0.2.0")
    .get()

android {
    namespace = "io.github.timkabot.performance.sdk"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    api(platform(commonLibs.compose.bom))
    api(commonLibs.kotlinx.coroutines.core)
    api(commonLibs.compose.runtime)
    api(commonLibs.compose.ui)
    api("androidx.fragment:fragment-ktx:1.8.5")
    implementation(commonLibs.kotlinx.coroutines.android)
    implementation("androidx.metrics:metrics-performance:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation(commonLibs.kotlinx.coroutines.test)
    testImplementation("androidx.navigation:navigation-compose:2.9.8")
}

tasks.withType<Test>().configureEach {
    testLogging { events("passed", "failed") }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "performance-sdk"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Performance SDK")
                description.set("Callback-based Android performance measurement SDK.")
            }
        }
    }
}
