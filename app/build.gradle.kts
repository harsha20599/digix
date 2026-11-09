plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Which smart home code to build. "sdk" needs Google's library in the local Maven folder, "fake" is
// a stand-in for testing on emulators, and "off" leaves Google Home out of the app.
val homeSdkFolder = file("${System.getProperty("user.home")}/.m2/repository/com/google/android/gms/play-services-home-types")
val homeSdkVersion = homeSdkFolder.list()?.filter { it.first().isDigit() }?.maxOrNull()
val homeBackend = providers.gradleProperty("digix.home").orNull ?: if (homeSdkVersion != null) "sdk" else "off"

android {
    namespace = "com.littyl.digix"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.littyl.digix"
        minSdk = 29
        // Deliberately old, because Android keeps these direct abilities for such apps: switching
        // Wi-Fi, switching the hotspot on Android 15 and below, and starting from the background.
        // Google Play won't accept new apps at this target, so a Play build needs its own flavor.
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Shrunk like release, but debuggable so the emulator test suite can read app state.
        create("staging") {
            initWith(getByName("release"))
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    sourceSets["main"].java.srcDir("src/home-$homeBackend/java")
    lint {
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    compileOnly(project(":hidden-stubs"))
    if (homeBackend == "sdk") {
        implementation("com.google.android.gms:play-services-home:$homeSdkVersion")
        implementation("com.google.android.gms:play-services-home-types:$homeSdkVersion")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
