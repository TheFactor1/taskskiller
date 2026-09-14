plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.thefactor1.taskskiller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thefactor1.taskskiller"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // One debug key for every machine and for CI. Without it each GitHub runner
    // signs with a freshly generated key, and Android refuses to install a build
    // over one signed by a different key, so every update needed an uninstall.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // The app deliberately uses privileged / restricted APIs behind runtime guards.
        disable += setOf("QueryAllPackagesPermission", "BatteryLife", "ProtectedPermissions")
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")

    // Shizuku: lets the app run privileged shell-level calls after a one-time
    // ADB handshake, with no root required.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // dadb: speaks the ADB wire protocol directly, so the app can reach the
    // box's own network-debugging port and start Shizuku without a computer.
    implementation("dev.mobile:dadb:2.0.0")
}
