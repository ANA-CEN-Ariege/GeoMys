# GeoMys Common

Application de saisie naturaliste pour GeoNature, pour Android **et** iOS, construite sur une seule base de code : Kotlin Multiplatform et Compose Multiplatform.

GeoMys Common est **indépendante** des applications GeoMys-Android et GeoMys-IOS. Elle a sa propre identité, et peut donc cohabiter avec elles sur un même téléphone. Elle n'en reprend pas les données.

**État : socle (lot 0).** L'application affiche un accueil provisoire. Le portage des fonctions de GeoMys-Android suit l'inventaire [`INVENTAIRE.md`](INVENTAIRE.md) (§ 10, ordre de portage).

## Structure

| Dossier | Contenu |
|---|---|
| `core/` | Logique commune, sans Compose : modèles, réseau, stockage, ViewModels… Cibles Android et iOS. |
| `ui/` | Écrans Compose Multiplatform, thème, ressources (`composeResources`). Sur iOS, ce module produit le framework `GeoMysUi`. |
| `androidApp/` | Application Android : `MainActivity`, manifeste, icônes. |
| `iosApp/` | Projet Xcode de l'application iOS, qui affiche l'interface Compose. |
| `INVENTAIRE.md`, `inventaire/` | Inventaire de ce qui est partageable dans GeoMys-Android, et suivi du portage. |

## Identité

| | Android | iOS |
|---|---|---|
| Identifiant | `fr.ariegenature.geomys.common` | `fr.ariegenature.geomys.common` |
| Nom sous l'icône | `androidApp/src/main/res/values/strings.xml` | `APP_DISPLAY_NAME` dans `iosApp/Configuration/Config.xcconfig` |
| Version | `versionName` / `versionCode` dans `androidApp/build.gradle.kts` | `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` dans `Config.xcconfig` |

- **Nom affiché** : « GeoMys Common ». Dans l'application, le titre vient de `ui/src/commonMain/composeResources/values/strings.xml`.
- **Logo** : celui de GeoMys. Les icônes sont reprises de GeoMys-Android et de GeoMys-IOS.

## Prérequis

- **Android Studio 2025.3** (JDK 21 intégré) et le **SDK Android 37**.
- **Xcode 27** avec le SDK iOS 26. L'application vise iOS 17 et plus.
- Sur ce Mac, `xcode-select` pointe vers les outils en ligne de commande. Deux solutions :
  - préfixer les commandes Xcode par `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` ;
  - ou exécuter une fois `sudo xcode-select -s /Applications/Xcode.app`.

## Compiler, lancer, tester

**Android**
- `./gradlew :androidApp:assembleDebug` produit l'APK dans `androidApp/build/outputs/apk/debug/`.
- Dans Android Studio, ouvrir le dossier racine et lancer la configuration `androidApp`.

**iOS**
- Dans Xcode, ouvrir `iosApp/iosApp.xcodeproj`, choisir le schéma `iosApp` et un simulateur, puis lancer (Run).
- La phase « Compile Kotlin Framework » compile le code Kotlin via `./gradlew :ui:embedAndSignAppleFrameworkForXcode`.
- En ligne de commande :

  ```
  DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
    -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' build
  ```

**Tests**
- `./gradlew :core:allTests` lance les tests communs sur la JVM et sur le simulateur iOS.

## Versions

| Outil | Version |
|---|---|
| Kotlin | 2.4.21 |
| Compose Multiplatform | 1.12.2 |
| AGP | 9.1.1 |
| Gradle | 9.3.1 |

AGP et Gradle sont les mêmes que dans GeoMys-Android. Toutes les versions sont regroupées dans `gradle/libs.versions.toml`.

## Règles du portage

- **GeoMys-Android et GeoMys-IOS sont des sources en lecture seule.** On copie, on ne modifie jamais.
- **Chaque fichier porté indique son commit d'origine**, dans un commentaire d'en-tête et dans `inventaire/index-fichiers.tsv` (colonnes `commit_reference` et `statut_portage`).

## Licence

GPL-3.0, comme GeoMys-Android. Voir [`LICENSE`](LICENSE).
