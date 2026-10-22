rootProject.name = "GeoMys"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// core : logique commune sans Compose (modèles, réseau, stockage, ViewModels…).
// ui : écrans Compose Multiplatform, communs à Android et iOS.
// androidApp : application Android (point d'entrée, manifeste).
// L'application iOS est le projet Xcode de iosApp/, qui embarque le framework produit par ui.
include(":core")
include(":ui")
include(":androidApp")
