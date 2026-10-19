# GeoMys Common

Application de saisie naturaliste pour GeoNature, pour Android **et** iOS, construite sur une seule base de code : Kotlin Multiplatform et Compose Multiplatform.

GeoMys Common est **indépendante** des applications GeoMys-Android et GeoMys-IOS. Elle a sa propre identité, et peut donc cohabiter avec elles sur un même téléphone. Elle n'en reprend pas les données.

**État : lots 0 à 9.** Le socle, les fondations (lot 1), la synchro des référentiels (lot 2), les envois (lot 3), le moteur de formulaires (lot 4), le socle des écrans (lot 5), les écrans OccTax (lot 6), ceux des Suivis (lot 7), ceux d'OccHab (lot 8), l'Explorer et le Maps Manager (lot 9) sont portés : tous les écrans de GeoMys-Android le sont. Les recettes terrain d'OccTax, d'OccHab et du lot 9 restent à faire, celle des Suivis est commencée. Les écrans reprennent GeoMys-Android ; sous iOS, ils prennent les styles de GeoMys-IOS. Les défauts de GeoMys-Android que le portage reproduisait sont corrigés ([`LOT-8.md`](LOT-8.md) § 5, [`LOT-9.md`](LOT-9.md) § 5). Reste le lot 10, la distribution. Le portage suit l'inventaire [`INVENTAIRE.md`](INVENTAIRE.md) (§ 10, ordre de portage).

**Écrans.** L'Accueil et les Paramètres (connexion, « Charger les données », sélections par défaut) sont ceux du lot 5. Une configuration incomplète ouvre les Paramètres au lancement, comme dans GeoMys-Android. Le bilan du lot, dont les deux allures des composants, est dans [`LOT-5.md`](LOT-5.md).

**Connexion GeoNature.** « Tester la connexion », dans les Paramètres, vérifie la connexion et l'authentification sur un serveur (lot 1). Le bilan du lot est dans [`LOT-1.md`](LOT-1.md).

**Synchro des référentiels.** La synchro complète de GeoMys-Android se lance par « Charger les données », dans les Paramètres, ou sans écran par le paramètre `action=synchroniser`. Le bilan du lot est dans [`LOT-2.md`](LOT-2.md).

**Envois.** Le moteur d'envoi de GeoMys-Android est porté, avec ses protections anti-doublon : OccTax, file des Suivis, OccHab, et les trois stores de saisie. Les écrans OccTax, ceux des Suivis et ceux d'OccHab le déclenchent. Le bilan du lot est dans [`LOT-3.md`](LOT-3.md).

**Formulaires.** Le moteur des formulaires de GeoMys-Android est porté : formulaire des Suivis (règles `hidden`, `change` et `required` conditionnel, bornes, champ espèce), valeurs d'une visite, champs additionnels OccTax. Les champs additionnels s'affichent dans les écrans OccTax, le formulaire des Suivis dans la nouvelle saisie du lot 7. Le bilan du lot est dans [`LOT-4.md`](LOT-4.md).

**Saisies OccTax.** Les écrans de saisie de GeoMys-Android sont portés : carte multi-taxons et trace GPS, relevé, caractérisation, dénombrement avec photos et sons, détails du relevé, saisie mono-taxon, Mes saisies, détail d'une saisie, import et export GPX. Des paramètres de lancement les ouvrent sur des données d'essai, sur l'émulateur et le simulateur seulement. Le bilan du lot, avec la recette terrain à faire, est dans [`LOT-6.md`](LOT-6.md).

**Suivis.** Les écrans des Suivis (Monitoring) de GeoMys-Android sont portés : liste des protocoles, détail d'un protocole, fiche d'un objet, nouvelle saisie et son formulaire, chaîne d'observations, Mes visites, carte des géométries. Le paramètre `action=monitoring-essai` charge des protocoles d'essai, sur l'émulateur et le simulateur seulement. Le bilan du lot, avec la recette terrain en cours, est dans [`LOT-7.md`](LOT-7.md).

**OccHab.** Les écrans d'OccHab de GeoMys-Android sont portés : carte d'édition des stations (point, polygone et ses trous, aimantage, import d'une station du serveur), informations obligatoires et détails d'une station, habitats, Mes stations. Le paramètre `action=occhab-essai` charge des stations d'essai, sur l'émulateur et le simulateur seulement. Le bilan du lot, avec la recette terrain à faire, est dans [`LOT-8.md`](LOT-8.md).

**Explorer et cartes.** L'Explorer (observations de la synthèse sur la zone visible) et le Maps Manager (zones téléchargées pour le hors-ligne, cartes MBTiles, ciblage d'un protocole) de GeoMys-Android sont portés ; les cartes MBTiles importées servent de fond sur tous les écrans-cartes, et chaque carte affiche l'attribution de son fond. Tous les fonds se téléchargent par zones, et un téléchargement s'annule. Le bilan du lot, avec la recette à faire, est dans [`LOT-9.md`](LOT-9.md).

**Prototypes du lot 0.** Ils ne s'ouvrent plus que par paramètre de lancement :
- `ecran=prototype-carte` : l'écran d'essais MapLibre, avec ses fonds, le MBTiles, le hors-ligne, le cap et les sommets déplaçables. Bilan dans [`PROTOTYPE-CARTE.md`](PROTOTYPE-CARTE.md), outils dans `outils/prototype-carte/` ;
- `ecran=mesure-taxref` : ce que coûte un référentiel TaxRef chargé en mémoire comme dans GeoMys-Android. Bilan dans [`MESURE-TAXREF.md`](MESURE-TAXREF.md).

## Structure

| Dossier | Contenu |
|---|---|
| `core/` | Logique commune, sans Compose : modèles, réseau, stockage, ViewModels… Cibles Android et iOS. |
| `ui/` | Écrans Compose Multiplatform, thème, ressources (`composeResources`). Sur iOS, ce module produit le framework `GeoMysUi`. |
| `androidApp/` | Application Android : `MainActivity`, service au premier plan du suivi GPS, manifeste, icônes. |
| `iosApp/` | Projet Xcode de l'application iOS, qui affiche l'interface Compose. |
| `outils/` | Outils de développement : la vérification avant de pousser (`verifier.sh`), les attendus d'org.json tirés des sources d'Android (`reference-org-json/`), un GeoNature simulé pour l'authentification (`geonature-simule/`), le serveur de tuiles et le générateur de MBTiles de test (`prototype-carte/`). |
| `licences/` | Texte des licences des codes repris d'autres projets (Apache 2.0 pour org.json, les icônes de Material Components et `ic_menu_delete` d'AOSP). |
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
- `./gradlew :core:allTests` lance les tests communs sur la JVM et sur le simulateur iOS. `-Pgeomys.simulateur=<UDID>` impose le simulateur des tests iOS, pour laisser tranquille un simulateur déjà ouvert.
- `./gradlew :ui:testAndroidHostTest` lance les tests des écrans (ViewModels et état des champs), sur la JVM ; `./gradlew :ui:iosSimulatorArm64Test`, ceux de mise en page, sur le simulateur iOS (même option `-Pgeomys.simulateur`).
- `outils/verifier.sh` fait la vérification complète, à lancer avant de pousser : APK Android, tests communs et tests des écrans (JVM et simulateur iOS), build iOS pour le simulateur. Il tient lieu d'intégration continue. Avec `--sans-ios`, il se passe de Xcode ; avec `--simulateur=<UDID>`, il passe le simulateur aux tests iOS.

## Versions

| Outil | Version |
|---|---|
| Kotlin | 2.4.21 |
| Compose Multiplatform | 1.12.2 |
| maplibre-compose (carte) | 0.20.0 |
| Navigation Compose (JetBrains) | 2.10.0-rc01 |
| Lifecycle ViewModel et Runtime Compose (JetBrains) | 2.11.0 |
| kotlinx-serialization | 1.11.0 |
| kotlinx-datetime | 0.8.0 |
| Ktor (client) | 3.6.0 |
| Kermit (journaux) | 2.2.0 |
| multiplatform-settings | 1.3.0 |
| atomicfu | 0.33.0 |
| AndroidX Core (Android, notification de synchro) | 1.18.0 |
| okio | 3.18.2 |
| AGP | 9.1.1 |
| Gradle | 9.3.1 |

AGP et Gradle sont les mêmes que dans GeoMys-Android. Toutes les versions sont regroupées dans `gradle/libs.versions.toml`.

## Règles du portage

- **GeoMys-Android et GeoMys-IOS sont des sources en lecture seule.** On copie, on ne modifie jamais.
- **Les écrans reprennent ceux de GeoMys-Android** au plus près : interfaces, styles, placement des éléments et fonctionnalités. Sous iOS, ils prennent les styles de GeoMys-IOS ; les éléments, textes et comportements restent ceux d'Android (décision 15 de l'inventaire, [`LOT-5.md`](LOT-5.md) § 2).
- **Chaque fichier porté indique son commit d'origine**, dans un commentaire d'en-tête et dans `inventaire/index-fichiers.tsv` (colonnes `commit_reference` et `statut_portage`).

## Licence

GPL-3.0, comme GeoMys-Android. Voir [`LICENSE`](LICENSE).
