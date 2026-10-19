import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Routes de navigation typées (@Serializable).
    alias(libs.plugins.kotlinxSerialization)
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
        // Les règles des écrans (ViewModels, état des champs) se vérifient sur la JVM, sans émulateur.
        withHostTest {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { cible ->
        cible.binaries.framework {
            baseName = "GeoMysUi"
            isStatic = true
        }
        // Le SQLite du système, pour les cartes MBTiles (core) : ici les tests ; l'appli le lie
        // dans le projet Xcode, le framework étant statique.
        cible.binaries.all { linkerOpts("-lsqlite3") }
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
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.maplibre.compose)
            implementation(libs.navigation.compose)
        }
        androidMain.dependencies {
            // Demande de permission (notification de la synchro, Android 13 et suivants).
            implementation(libs.androidx.activity.compose)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.okio)
        }
        // La mise en page des écrans se vérifie sur un simulateur iOS (tests Compose sans fenêtre).
        iosTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.compose.uiTest)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.okio)
        }
    }
}

// Le simulateur des tests iOS, comme pour core : `-Pgeomys.simulateur=<UDID>` en impose un.
providers.gradleProperty("geomys.simulateur").orNull?.let { udid ->
    tasks.withType<KotlinNativeSimulatorTest>().configureEach { device.set(udid) }
}

compose.resources {
    packageOfResClass = "fr.ariegenature.geomys.ui.ressources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
