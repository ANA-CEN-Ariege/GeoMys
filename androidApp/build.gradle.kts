import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "fr.ariegenature.geomys.common"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Identité propre : GeoMys Common est une application indépendante de GeoMys, et les
        // deux peuvent cohabiter sur un même téléphone.
        applicationId = "fr.ariegenature.geomys.common"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(projects.ui)
    implementation(libs.androidx.activity.compose)
}
