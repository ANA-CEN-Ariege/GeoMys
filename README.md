# GeoMys Common

Application de saisie naturaliste pour GeoNature, pour Android **et** iOS, construite sur une seule base de code : Kotlin Multiplatform et Compose Multiplatform.

GeoMys Common est **indépendante** des applications GeoMys-Android et GeoMys-IOS. Elle a sa propre identité, et peut donc cohabiter avec elles sur un même téléphone. Elle n'en reprend pas les données.

**État : socle (lot 0).** L'application affiche un accueil provisoire. Le portage des fonctions de GeoMys-Android suit l'inventaire [`INVENTAIRE.md`](INVENTAIRE.md) (§ 10, ordre de portage).

**Prototype carte.** Le bouton « Prototype carte » de l'accueil ouvre l'écran d'essais MapLibre, avec ses fonds, le MBTiles, le hors-ligne, le cap et les sommets déplaçables. Son bilan est dans [`PROTOTYPE-CARTE.md`](PROTOTYPE-CARTE.md), et ses outils dans `outils/prototype-carte/`.

**Connexion GeoNature.** Le bouton « Connexion GeoNature » vérifie la connexion et l'authentification sur un serveur (lot 1). Le bilan du lot est dans [`LOT-1.md`](LOT-1.md).

**Mesure mémoire TaxRef.** Le bouton « Mesure mémoire TaxRef » mesure ce que coûte un référentiel TaxRef chargé en mémoire comme dans GeoMys-Android. Son bilan est dans [`MESURE-TAXREF.md`](MESURE-TAXREF.md).

## Structure

| Dossier | Contenu |
|---|---|
| `core/` | Logique commune, sans Compose : modèles, réseau, stockage, ViewModels… Cibles Android et iOS. |
| `ui/` | Écrans Compose Multiplatform, thème, ressources (`composeResources`). Sur iOS, ce module produit le framework `GeoMysUi`. |
| `androidApp/` | Application Android : `MainActivity`, manifeste, icônes. |
| `iosApp/` | Projet Xcode de l'application iOS, qui affiche l'interface Compose. |
| `outils/` | Outils de développement : la vérification avant de pousser (`verifier.sh`), les attendus d'org.json tirés des sources d'Android (`reference-org-json/`), un GeoNature simulé pour l'authentification (`geonature-simule/`), le serveur de tuiles et le générateur de MBTiles de test (`prototype-carte/`). |
| `licences/` | Texte des licences des codes repris d'autres projets (Apache 2.0 pour org.json). |
| `docs/` | Captures et documents annexes. |
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
- `./gradlew :androidApp:assembleMesure` produit une release signée avec la clé de debug, pour mesurer durées et mémoire sans les ralentissements d'une appli débogable.
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
- `outils/verifier.sh` fait la vérification complète, à lancer avant de pousser : APK Android, tests communs (JVM et simulateur iOS) et build iOS pour le simulateur. Il tient lieu d'intégration continue. Avec `--sans-ios`, il se passe de Xcode.

## Versions

| Outil | Version |
|---|---|
| Kotlin | 2.4.21 |
| Compose Multiplatform | 1.12.2 |
| maplibre-compose (carte) | 0.20.0 |
| kotlinx-serialization | 1.11.0 |
| kotlinx-datetime | 0.8.0 |
| Ktor (client) | 3.6.0 |
| Kermit (journaux) | 2.2.0 |
| multiplatform-settings | 1.3.0 |
| okio | 3.18.2 |
| AGP | 9.1.1 |
| Gradle | 9.3.1 |

AGP et Gradle sont les mêmes que dans GeoMys-Android. Toutes les versions sont regroupées dans `gradle/libs.versions.toml`.

## Règles du portage

- **GeoMys-Android et GeoMys-IOS sont des sources en lecture seule.** On copie, on ne modifie jamais.
- **Chaque fichier porté indique son commit d'origine**, dans un commentaire d'en-tête et dans `inventaire/index-fichiers.tsv` (colonnes `commit_reference` et `statut_portage`).

## Licence

GPL-3.0, comme GeoMys-Android. Voir [`LICENSE`](LICENSE).
