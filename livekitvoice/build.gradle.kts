plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.livekit.voiceassistant"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "../proguard-common.pro")
        }
        debug {
            isMinifyEnabled = false
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
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    // 配置Java编译选项，禁用警告
    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf(
            "-nowarn",               // 禁用所有警告
            "-Xlint:none",           // 禁用所有lint警告
            "-Xlint:-deprecation",   // 禁用废弃API警告
            "-Xlint:-unchecked",     // 禁用未检查转换警告
            "-Xlint:-rawtypes",      // 禁用原始类型警告
            "-Xlint:-varargs",       // 禁用可变参数警告
            "-Xlint:-cast",          // 禁用类型转换警告
            "-Xlint:-serial"         // 禁用序列化警告
        ))
        // 确保编译器不报告任何警告
        options.isWarnings = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
dependencies {
    // LiveKit 核心库
    api("io.livekit:livekit-android:2.18.3")
    api("io.livekit:livekit-android-compose-components:1.4.0")

    // AndroidX 核心库 - 升级到最新版本以兼容 Kotlin 2.0.21
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    // Lifecycle 库 - 升级到最新版本
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Compose 库 - 使用最新的 BOM 版本以兼容 Kotlin 2.0.21
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // 权限管理 - 使用最新版本
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")

    // 网络和工具库
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("com.github.ajalt:timberkt:1.5.1")

    // 音频动画
    implementation("com.aghajari.waveanimation:AXWaveAnimation:1.0.0")

    // 测试依赖
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    
    // Debug 工具
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}