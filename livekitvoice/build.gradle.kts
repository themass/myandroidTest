plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.livekit.android.example.voiceassistant"
    compileSdk = rootProject.ext.get("compileSdkVersion") as Int

    defaultConfig {
        minSdk = rootProject.ext.get("minSdkVersion") as Int
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.8"
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
dependencies {
    // For local development with the LiveKit Compose SDK only.
    // implementation("io.livekit:livekit-compose-components")

    api("io.livekit:livekit-android:2.9.0")
    api("io.livekit:livekit-android-compose-components:1.3.0")
    implementation ("androidx.lifecycle:lifecycle-livedata-ktx:${rootProject.ext.get("lifecycle_livedata_ktx")}")
    implementation("androidx.core:core-ktx:${rootProject.ext.get("kotlin_ktx")}")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${rootProject.ext.get("lifecycle_livedata_ktx")}")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("com.github.ajalt:timberkt:1.5.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.recyclerview:recyclerview:1.4.0")


//    implementation("com.github.xfans:VoiceWaveView:1.0.2")
//    implementation( "com.github.Jay-Goo:WaveLineView:v1.0.4")
//    implementation( "com.github.Jay-Goo:AndroidMP3Recorder:v1.0.7")
    implementation( "com.aghajari.waveanimation:AXWaveAnimation:1.0.0")
//    implementation( "com.skyfishjy.ripplebackground:library:1.0.1")
//    implementation( "com.cleveroad:audiovisualization:1.0.1")
//    implementation( "com.yalantis:eqwaves:1.0.1")
//    implementation( "com.github.GitHubZJY:AudioVisualizeView:v1.0.2")
//    implementation( "org.firezenk:audiowaves:1.1@aar")
//    implementation( "com.narayanacharya:WaveView:0.9.4")

}