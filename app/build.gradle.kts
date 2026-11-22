plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.myhealth"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        applicationId = "app.myhealth"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    androidResources {
        // Only ship the three supported UI languages (and drop library translations we cannot QA).
        localeFilters += listOf("en", "hi", "gu")
    }
    // Release signing comes only from the environment (CI secrets or a local shell); nothing is committed.
    val keystorePath = System.getenv("MYHEALTH_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("MYHEALTH_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("MYHEALTH_KEY_ALIAS")
            keyPassword = System.getenv("MYHEALTH_KEY_PASSWORD")
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    packaging {
        // Post-quantum parameter tables from Bouncy Castle (pulled in by PdfBox) are never used: PDF decryption
        // only needs classic ciphers. Saves ~7 MB.
        resources { excludes += listOf("org/bouncycastle/pqc/**", "META-INF/versions/9/OSGI-INF/MANIFEST.MF") }
    }
    bundle {
        // In-app language switching needs all three languages on the device (no Play language splits).
        language { enableSplit = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { t ->
            // Opt-in design screenshots: -Pscreenshots [-Pthemes=Aurora,Crystal] [-Pmodes=Dark,Light]
            if (project.hasProperty("screenshots")) {
                t.systemProperty("screenshots", "1")
                t.systemProperty("screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
                listOf("themes", "modes").forEach { k -> project.findProperty(k)?.let { v -> t.systemProperty(k, v.toString()) } }
            }
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        error += listOf("HardcodedText", "MissingTranslation", "ExtraTranslation")
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.fragment)
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.sqlite)
    testImplementation(libs.androidx.sqlite.framework)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
