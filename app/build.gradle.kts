// ✅ Required to read from local.properties
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id ("kotlin-parcelize")
    id("org.jetbrains.kotlin.kapt")
}

// ✅ Load COHERE_API_KEY from local.properties
val localProperties = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}
val cohereApiKey = localProperties.getProperty("COHERE_API_KEY") ?: ""

android {
    namespace = "com.labactivity.crammode"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.labactivity.crammode"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ Inject the COHERE_API_KEY as a BuildConfig field
        buildConfigField("String", "COHERE_API_KEY", "\"$cohereApiKey\"")
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
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("androidx.activity:activity-ktx:1.8.0")


    implementation ("com.google.android.material:material:1.12.0") // or latest

    implementation("com.vanniktech:android-image-cropper:4.6.0")

    implementation ("com.squareup.okhttp3:logging-interceptor:4.12.0")


    implementation("androidx.cardview:cardview:1.0.0")



    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")




    // Cohere + Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.1")


    // CameraX
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.firebaseui:firebase-ui-firestore:8.0.0")
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // AndroidX & UI
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")

    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.media3:media3-common:1.1.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
