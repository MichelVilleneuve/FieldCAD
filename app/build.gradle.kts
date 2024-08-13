plugins {
    id ("com.android.application")
    id ("org.jetbrains.kotlin.android")
}

android {
    namespace = ("com.michelvilleneuve.fieldcad")
    compileSdk = 34

    defaultConfig {
        applicationId = "com.michelvilleneuve.fieldcad"
        minSdk =21
        targetSdk =34
        versionCode =1
        versionName ="1.0"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner ="androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles (getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
    buildFeatures {
        //noinspection DataBindingWithoutKapt
        dataBinding = true
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx.v190)
    implementation(libs.androidx.appcompat.v161)
    implementation(libs.material.v180)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.material3.android)
    implementation(libs.generativeai)
    implementation(libs.play.services.mlkit.text.recognition.common)
    implementation(libs.vision.common)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v115)
    androidTestImplementation(libs.androidx.espresso.core.v351)
    implementation (libs.gson)
    implementation (libs.text.recognition)
}
