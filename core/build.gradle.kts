import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
}

// Logique commune, sans dépendance à Compose : elle se compile et se teste sans l'UI.
kotlin {
    android {
        namespace = "fr.ariegenature.geomys.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        // Les tests communs tournent aussi sur la JVM (tests « host » Android), sans émulateur.
        withHostTest {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { cible ->
        // os/proc.h (os_proc_available_memory) n'est pas dans les bibliothèques de plateforme
        // de Kotlin/Native : définition dans src/nativeInterop/cinterop/proc.def.
        cible.compilations.getByName("main").cinterops.create("proc")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.atomicfu)
            implementation(libs.kermit)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.json.okio)
            api(libs.ktor.client.core)
            api(libs.multiplatform.settings)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
