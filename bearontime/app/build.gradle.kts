plugins {
    id("com.android.application")
}

android {
    namespace = "com.bearfamily.app.bearontime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bearfamily.app.bearontime"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0-alpha2"
        manifestPlaceholders["appLabel"] = "熊正點報時"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "熊正點報時 DEV"
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["appLabel"] = "熊正點報時"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
