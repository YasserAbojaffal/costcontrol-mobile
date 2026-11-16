plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.islandeden.costcontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.islandeden.costcontrol"
        minSdk = 24
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
        // عنوان الخادم الافتراضي — يمكن تغييره من داخل التطبيق (أيقونة الإعدادات في شاشة الدخول)
        buildConfigField("String", "DEFAULT_SERVER", "\"https://cost.spirttravel.com/\"")
        resourceConfigurations += listOf("ar", "en")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/release.jks")
            storePassword = System.getenv("CC_KS_PASS") ?: "IslandEden#CC2026"
            keyAlias = "costcontrol"
            keyPassword = System.getenv("CC_KS_PASS") ?: "IslandEden#CC2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
