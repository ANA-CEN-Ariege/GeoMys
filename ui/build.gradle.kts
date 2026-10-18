import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Écrans Compose Multiplatform, communs à Android et iOS. Sur iOS, ce module produit le
// framework GeoMysUi qu'embarque le projet Xcode (iosApp).
kotlin {
    android {
        namespace = "fr.ariegenature.geomys.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        // Nécessaire pour que les ressources Compose (logo, chaînes) soient embarquées sur Android.
        androidResources {
            enable = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { cible ->
        cible.binaries.framework {
            baseName = "GeoMysUi"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api : InfosApp (core) fait partie de la signature de GeoMysApp.
            api(projects.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.maplibre.compose)
        }
    }
}

compose.resources {
    packageOfResClass = "fr.ariegenature.geomys.ui.ressources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
