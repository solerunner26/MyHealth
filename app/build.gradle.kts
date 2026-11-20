import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Optional Google Sign-In configuration.
 * Provide the Web client id of your OAuth client either in gradle.properties
 * (`MYHEALTH_GOOGLE_WEB_CLIENT_ID=...`) or with `-PMYHEALTH_GOOGLE_WEB_CLIENT_ID=...`.
 * When absent, the app still runs fully: the "continue without an account" path is always available.
 */
val googleWebClientId: String = (project.findProperty("MYHEALTH_GOOGLE_WEB_CLIENT_ID") as String?) ?: ""

android {
    namespace = "com.solerunner.myhealth"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.solerunner.myhealth"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signing is taken from CI/local secrets when they are present.
            // Without them the release variant is still assembled, just unsigned.
            val storeFilePath = (project.findProperty("MYHEALTH_KEYSTORE") as String?)
            if (storeFilePath != null) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(storeFilePath)
                    storePassword = project.findProperty("MYHEALTH_KEYSTORE_PASSWORD") as String?
                    keyAlias = project.findProperty("MYHEALTH_KEY_ALIAS") as String?
                    keyPassword = project.findProperty("MYHEALTH_KEY_PASSWORD") as String?
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
            )
        }
        jniLibs { useLegacyPackaging = false }
    }

    lint {
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
        sarifReport = false
        baseline = file("lint-baseline.xml").takeIf { it.exists() }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    // On-device document intelligence: bundled OCR model (works with no network) + PDF text extraction
    implementation(libs.mlkit.text.recognition)
    implementation(libs.pdfbox.android)

    // Encrypted local database
    implementation(libs.sqlcipher.android)

    // Optional device telemetry (Health Connect = Fitbit/Google Health data on-device)
    implementation(libs.androidx.health.connect.client)

    // Sign in with Google (optional; the app is fully usable without any account)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.junit)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
