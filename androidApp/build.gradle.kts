import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Signature des versions, comme GeoMys-Android : la clé et ses mots de passe viennent de
// `keystore.properties`, à la racine du projet, que git ignore ; la clé elle-même vit hors du dépôt.
// Sans ce fichier, `assembleRelease` produit un APK non signé et le reste se construit normalement.
val keystoreProperties = Properties().apply {
    val fichier = rootProject.file("keystore.properties")
    if (fichier.exists()) fichier.inputStream().use { load(it) }
}
val signatureDisponible = keystoreProperties.getProperty("storeFile") != null

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
        // Les téléphones seulement (ARM 64 et 32 bits, comme le Galaxy A12) : le moteur de MapLibre pèse
        // une dizaine de mégaoctets par architecture. L'émulateur des Mac Apple Silicon est aussi en arm64.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        // Les paramètres de lancement des essais (MainActivity) : versions de test seulement.
        buildConfigField("boolean", "ESSAIS", "true")
    }
    buildFeatures {
        buildConfig = true
    }
    signingConfigs {
        if (signatureDisponible) {
            create("release") {
                storeFile = File(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            // R8, comme GeoMys-Android : code et ressources inutilisés retirés (proguard-rules.pro).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "ESSAIS", "false")
            if (signatureDisponible) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // La release signée avec la clé de debug, pour mesurer durées et mémoire sur un téléphone
        // sans les ralentissements d'une appli débogable : ./gradlew :androidApp:assembleMesure.
        create("mesure") {
            initWith(getByName("release"))
            buildConfigField("boolean", "ESSAIS", "true")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    // Le moteur de MapLibre compressé dans l'APK : 14 Mo à télécharger au lieu de 27. Android l'extrait à
    // l'installation, pour une place occupée équivalente sur le téléphone.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // kotlinx-datetime s'appuie sur java.time, absent avant Android 8 : sans ce désucrage, la
        // première date manipulée plantait sous Android 7 (minSdk 24).
        isCoreLibraryDesugaringEnabled = true
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
    implementation(libs.androidx.core.ktx)
    // Moteur de rendu de la carte MapLibre. OpenGL plutôt que Vulkan : compatible avec plus
    // d'appareils (minSdk 24) et avec l'émulateur.
    runtimeOnly(libs.maplibre.compose.runtime.opengl.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
