plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.soupslurpr.appverifier"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.roundsalmon4.appverifier"
        minSdk = 28
        targetSdk = 35
        versionCode = 13
        versionName = versionCode.toString() + (project.findProperty("versionSuffix") ?: "")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en")
    }
    signingConfigs {
        create("fromKeystore") {
            val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (releaseKeystorePath != null) {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: error("RELEASE_KEYSTORE_PASSWORD not set")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: error("RELEASE_KEY_ALIAS not set")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: error("RELEASE_KEY_PASSWORD not set")
            } else {
                val keystoreFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("fromKeystore")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("fromKeystore")
        }
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("fromKeystore")
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.accompanist.drawablepainter)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
