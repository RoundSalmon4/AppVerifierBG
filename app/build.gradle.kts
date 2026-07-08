plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("gradle/version.gradle.kts"))

val appVersionCode: Int by extra
val appVersionMajor: Int by extra
val appVersionMinor: Int by extra
val appVersionPatch: Int by extra

android {
    namespace = "dev.soupslurpr.appverifier"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.roundsalmon4.appverifier"
        minSdk = 28
        targetSdk = 36
        versionCode = appVersionCode
        versionName = (project.findProperty("versionOverride") as? String) ?: ("$appVersionMajor.$appVersionMinor.$appVersionPatch" + (project.findProperty("versionSuffix") ?: ""))

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
                enableV3Signing = true
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

    constraints {
        implementation("androidx.core:core") {
            version {
                strictly(libs.versions.core.ktx.get())
            }
        }
    }

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
