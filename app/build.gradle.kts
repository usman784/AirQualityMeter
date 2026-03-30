import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.air.quality.meter"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.air.quality.meter"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load API keys from local.properties
        val localProperties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        val owmKey = localProperties.getProperty("OWM_API_KEY") ?: "YOUR_OWM_API_KEY_HERE"
        buildConfigField("String", "OWM_API_KEY", "\"$owmKey\"")
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    aaptOptions {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)

    // Navigation Component
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // FCM (Firebase Cloud Messaging)
    implementation(libs.firebase.messaging)

    // FirebaseUI
    implementation(libs.firebase.ui.auth)

    // Google Sign-In
    implementation(libs.play.services.auth)

    // Country Code Picker
    implementation(libs.countryCodePicker)

    // Networking (Retrofit + OkHttp + Gson)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Room (offline cache)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // WorkManager (background sync / AQI alerts)
    implementation(libs.workmanager.ktx)

    // Coroutines
    implementation(libs.coroutines.android)

    // Lifecycle (ViewModel + LiveData)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Glide (image loading)
    implementation(libs.glide)

    // MPAndroidChart (AQI history graphs)
    implementation(libs.mpandroidchart)

    // FusedLocationProvider (GPS for dashboard AQI fetch)
    implementation(libs.play.services.location)

    // SwipeRefreshLayout (pull-to-refresh on dashboard)
    implementation(libs.swiperefreshlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}