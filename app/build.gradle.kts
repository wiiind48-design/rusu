plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rusuden.app"
    compileSdk = 34

    defaultConfig {
        // 旧IDで入ったままの端末と衝突しないよう v2 に変更した(署名切替の際の移行措置)
        applicationId = "com.rusuden.app.v2"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.2.2"
    }

    // ビルド環境が変わっても同じ署名になるよう、鍵をリポジトリに同梱している。
    // (個人利用アプリのため。ストア公開する場合は鍵を差し替えて秘匿すること)
    signingConfigs {
        create("shared") {
            storeFile = file("rusuden.keystore")
            storePassword = "rusuden-local-key"
            keyAlias = "rusuden"
            keyPassword = "rusuden-local-key"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
