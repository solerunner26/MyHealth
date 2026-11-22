plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.myhealth.data"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}

dependencies {
    api(project(":domain"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.sqlcipher.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
