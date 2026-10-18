# Prototype carte (lot 0) : bilan

*18 octobre 2026. maplibre-compose 0.20.0. Moteur de rendu OpenGL sur Android, Metal sur iOS. Essais faits sur l'émulateur Android Pixel_10 (Android 17) et sur le simulateur iPhone 17 Pro (iOS 26.5).*

L'inventaire (`INVENTAIRE.md` § 5) faisait de la carte le point critique du portage. GeoMys-Android repose sur osmdroid, archivé depuis novembre 2024, et l'édition des géométries OccTax et OccHab demande des sommets déplaçables, que maplibre-compose ne fournit pas. Ce prototype vérifie, sur les deux plateformes, les cinq capacités jugées à risque.

**Verdict : MapLibre via maplibre-compose est retenu pour GeoMys Common.**
- Quatre points sur cinq fonctionnent, dont le plus risqué, le glisser des sommets.
- Le cinquième, le User-Agent, ne se réglait pas comme prévu : le contournement prévu a été mis en place au lot 1 (§ 2).
- Le glisser des sommets est confirmé au doigt sur les deux téléphones, et la rotation selon le cap est fluide sur téléphone après correction (§ 3).

## 1. Résultats

| # | Capacité | Android | iOS | Preuves |
|---|---|---|---|---|
| 1 | **Glisser un sommet**, forme redessinée en direct | ✅ émulateur et Galaxy A12 | ✅ iPhone 16 | `docs/prototype-carte/android-2-sommet-glisse.png`, puis essai au doigt sur les deux téléphones |
| 2 | **Fond MBTiles** embarqué | ✅ | ✅ | `android-3-mbtiles.png`, `ios-3-mbtiles.png` |
| 3 | **Zone hors-ligne** : téléchargement, puis affichage sans réseau | ✅ | ✅ | `android-4-hors-ligne-serveur-coupe.png`, `ios-4a-…`, `ios-4b-…` |
| 4 | **User-Agent** des requêtes de tuiles | ❌ pas appliqué ; ✅ depuis le lot 1, tuiles servies par l'appli | idem | journaux du serveur de test (§ 2) |
| 5 | **Cap** et carte qui tourne avec | ✅ | ✅ iPhone 16, rotation fluide après correction (§ 1.4) | `android-5-cap.jpg`, puis essai sur téléphone |

Fonds en ligne (Plan IGN, photos IGN, OSM) : ✅ sur les deux plateformes (`android-1-plan-ign.jpg`, `ios-1-plan-ign.jpg`).

### 1.1 Glisser un sommet

**Principe.** Chaque sommet est une poignée Compose de 44 dp posée au-dessus de la carte (`ui/…/carte/PoigneesSommets.kt`).
- Elle est placée par `screenLocationFromPosition`, et recalculée à chaque mouvement de caméra, rotation comprise.
- Elle consomme ses gestes : la carte ne se déplace pas pendant qu'on la tire.
- Le polygone est une source GeoJSON reconstruite à chaque mouvement du doigt (`synchronousTiling = true`).

**Essai Android** (geste simulé par `adb`). Le sommet suit le doigt **au pixel près**, la forme se redessine en continu et la carte reste immobile. Cela marche aussi sur une carte tournée.

Une première version cumulait les déplacements et retardait d'environ 8 dp, à cause du seuil de détection du glisser. Elle a été corrigée : le sommet se cale désormais sur la position absolue du doigt.

**Sur les téléphones.** Le glisser a été confirmé au doigt le 18 octobre, sur un Samsung Galaxy A12 (Android 12) et sur un iPhone 16. Côté iOS, cet essai à la main était indispensable : le simulateur ne sait pas simuler de toucher en ligne de commande.

Le risque relevé par l'inventaire, le partage des touchers entre Compose et une vue carte UIKit, disparaît. Dans maplibre-compose 0.20.0, la carte est dessinée dans une surface Compose sur les deux plateformes, sans vue native intercalée.

### 1.2 Fond MBTiles

`rememberMbtilesUrl(Res.getUri("files/carte-test.mbtiles"))` copie le fichier embarqué dans un fichier local et renvoie une URL `mbtiles://`, que MapLibre lit par SQLite. C'est le même code sur Android et sur iOS.

Le fichier de test (135 tuiles, zooms 10 à 16, autour de Foix) est produit par `outils/prototype-carte/carte_test.py mbtiles`.

### 1.3 Zone hors-ligne

`OfflineStorage.create(OfflinePackDefinition.TilePyramid(…))` télécharge l'emprise visible, du zoom courant à deux niveaux de plus. Résultats :
- **Android** : 92 tuiles, 93 ressources sur 93, statut `Complete`.
- **iOS** : 81 tuiles, 82 ressources sur 82, statut `Complete`.

**La preuve.** Le serveur de test est arrêté et la carte dézoomée d'un cran. On affiche alors des tuiles que seul le paquet contient, le damier violet du niveau 15. Elles restent nettes dans l'emprise téléchargée, alors qu'en dehors on ne voit qu'une vieille tuile agrandie, faute de réseau. Le paquet survit aussi au redémarrage de l'appli.

**Ce qui en découle pour le code** :
- **Style par URL.** Un paquet se définit par l'URL d'un style. Les styles sont générés en code (`FondCarte.kt`) et servis par l'appli elle-même sous `geomys://styles/<fond>.json`, grâce à un `MapResourceProvider`. La même URL sert à l'affichage et au téléchargement. Ça fonctionne sur les deux plateformes.
- **Correspondance des zooms.** Pour des tuiles raster de 256 px, un paquet de zooms 14 à 16 récupère les niveaux de tuiles 15 à 17. Il faut en tenir compte dans l'estimation du volume.
- **Pas d'OSM.** Le bouton est désactivé sur le fond OSM, dont la politique d'usage interdit le téléchargement en masse.

### 1.4 Cap et rotation

**Android.** `rememberDefaultHeadingProvider()` fournit le cap. Quand le champ magnétique simulé de l'émulateur tourne, le cap affiché passe de 360° à environ 280°. La carte pivote avec lui, et les poignées restent sur les sommets. La boussole native de MapLibre apparaît.

**Référence du nord.** Elle est **magnétique**, faute de position connue pour calculer la déclinaison (`HeadingReference.MagneticNorth`). C'est le comportement actuel de GeoMys-Android ; le choix entre nord magnétique et géographique reste à trancher (`INVENTAIRE.md` § 8.3).

**iOS.** Le simulateur n'a pas de boussole. Sur l'iPhone 16, le cap fonctionne, mais la rotation était saccadée.

**Fluidité.** Deux causes, toutes deux dans le prototype et non dans la bibliothèque :
- **Trop peu de mesures.** Le réglage par défaut (`HeadingRequest.Standard`) ne garde qu'**une mesure par seconde** : le fournisseur iOS échantillonne le flux à ce rythme.
- **Pas de transition.** Chaque mesure était appliquée d'un coup, sans transition.

**Correction.**
- Le prototype demande désormais toutes les mesures (`minimumInterval = Duration.ZERO`).
- À chaque image affichée, le cap de la carte glisse vers la dernière mesure. Le calcul vit dans `LissageCap`, logique commune de `core/…/geo/` testée sur la JVM et iOS :
  - le plus court chemin autour du nord ;
  - 63 % de l'écart rattrapé en 150 ms ;
  - une zone morte de 0,5° contre le tremblement du capteur.
- GeoMys-Android, lui, applique chaque mesure du capteur sans lissage.

## 2. Constat : les en-têtes de l'intercepteur ne partent pas

`DefaultMapRuntime.configure { requestInterceptor = MapRequestInterceptor(headers = …) }` devait poser le User-Agent `GeoMysCommon/<version> (<système>)`. Le serveur de test, qui journalise tous les en-têtes reçus, montre :

| | En-têtes reçus | User-Agent |
|---|---|---|
| Android | `accept-encoding`, `accept`, `host`, `user-agent` | `MapLibreNative/1.0` |
| iOS | `Host`, `Accept`, `Accept-Language`, `Connection`, `Accept-Encoding`, `User-Agent` | `GeoMysCommon/0.1.0 MapLibreNative/0.0.0 (00000000) iOS Simulator/26.5.0 (arm64)` |

Un en-tête témoin (`X-GeoMys`), ajouté pour l'essai, n'arrive pas non plus. **Aucun en-tête de l'intercepteur n'est donc appliqué**, sur aucune des deux plateformes. Aucun avertissement n'est journalisé, alors que le fournisseur de ressources, configuré au même endroit, fonctionne.

Sur iOS, MapLibre compose lui-même un User-Agent qui contient le nom et la version de l'appli, tirés du bundle : l'appli est identifiée. Sur Android, le User-Agent est générique.

**Conséquences et pistes**
- **IGN.** Pour les tuiles IGN de la Géoplateforme, aucune conséquence : elles ne demandent pas de User-Agent particulier.
- **OSM.** La politique d'usage d'OSM demande un User-Agent qui identifie l'appli, et sur Android il est générique. Deux pistes :
  - **servir les tuiles nous-mêmes**, par un `MapResourceProvider` sur un schéma maison qui va chercher les tuiles avec Ktor. On maîtrise alors les en-têtes, les nouvelles tentatives et la politique de cache, et les paquets hors-ligne passent aussi par là. À essayer au lot 1, quand Ktor sera en place ;
  - **signaler l'anomalie** au projet maplibre-compose. Ce serait une démarche publique : je ne l'ai pas faite sans ton accord.
- **Suivi.** Le commentaire de `ConfigurationCarte.kt` rappelle de revérifier à chaque montée de version.

**Résolu au lot 1, par la première piste.** `ui/…/carte/TuilesHttp.kt` télécharge lui-même, avec Ktor, les tuiles d'OpenStreetMap (et celles du serveur de test).
- Il passe par la forme générale du fournisseur de ressources, `MapResourceProvider(accepts, load)`. Celle-ci reçoit les requêtes de la carte, avec l'ETag et les dates de la version en cache, et rend les octets avec leurs informations de cache, ou une réponse 304.
- Le cache de MapLibre continue donc de fonctionner.
- L'intercepteur, sans effet, a été retiré.

Vérifié le 18 octobre sur l'émulateur Android et le simulateur iOS :

| | User-Agent reçu par le serveur de test |
|---|---|
| Android | `GeoMysCommon/0.1.0 (Android 17)`, au lieu de `MapLibreNative/1.0` |
| iOS | `GeoMysCommon/0.1.0 (iOS 26.5)` |

- Les tuiles s'affichent normalement.
- Le téléchargement d'une zone hors-ligne passe par le même chemin : 149 tuiles, statut `Complete`, toutes les requêtes avec ce User-Agent.
- Le fond OpenStreetMap réel s'affiche sur les deux plateformes.

## 3. Vérifications à la main

1. **Glisser un sommet** : ✅ confirmé au doigt le 18 octobre, sur le Galaxy A12 et sur l'iPhone 16.
2. **Cap** : ✅ fonctionne sur l'iPhone 16. La rotation, d'abord saccadée, est corrigée (§ 1.4). Elle a été jugée fluide sur téléphone le 18 octobre.
   - Activer « Carte orientée selon le cap » et tourner sur soi.
   - Attendu : la carte pivote. La référence affichée sera `TrueNorth` si la position est connue.

## 4. Reproduire les essais

**Serveur de tuiles de test.** Il écoute seulement en local et journalise le User-Agent, ainsi que tous les en-têtes avec `--entetes` :

```
python3 outils/prototype-carte/carte_test.py serveur --entetes --journal requetes-tuiles.log
```

**Paramètres de lancement.** Le prototype s'ouvre directement avec un fond, un zoom et, si besoin, un téléchargement automatique :
- `ecran=prototype-carte` ;
- `fond=plan-ign|ortho-ign|osm|mbtiles-test|serveur-test` ;
- `zoom=<n>` ;
- `action=hors-ligne`.

```
# Android (émulateur)
adb shell am start -n fr.ariegenature.geomys.common/.MainActivity \
  --es ecran prototype-carte --es fond serveur-test --es action hors-ligne

# iOS (simulateur)
xcrun simctl launch <appareil> fr.ariegenature.geomys.common \
  -ecran prototype-carte -fond serveur-test -action hors-ligne
```

**Joindre le Mac depuis les appareils.** L'émulateur Android joint le Mac par `10.0.2.2`, le simulateur iOS par `localhost`. Le HTTP en clair est autorisé comme dans les applis d'origine : partout sous Android (`androidApp/src/main/res/xml/network_security_config.xml`, copié de GeoMys-Android au lot 2), vers les adresses locales seulement sous iOS (`NSAllowsLocalNetworking`, comme GeoMys-IOS).

## 5. Ce qui reste du prototype dans le code

- **Gardé : la couche carte commune `ui/…/carte/`**, qui isole maplibre-compose :
  - `FondCarte` : fonds IGN et OSM, style raster généré ;
  - `ConfigurationCarte` : runtime, fournisseur `geomys://` ;
  - `PoigneesSommets` : poignées déplaçables ;
  - `LatLon`, dans `core/…/geo/`.
- **Provisoire, à retirer** quand les vrais écrans carte arriveront :
  - l'écran `ui/…/carte/prototype/PrototypeCarteScreen.kt`, son bouton d'accueil ;
  - les fonds de test (`mbtiles-test`, `serveur-test`) et le fichier `composeResources/files/carte-test.mbtiles` ;
  - les exceptions HTTP locales.
- **Taille** : l'APK debug fait 54 Mo, car le moteur MapLibre natif y figure pour trois architectures. Un bundle Play ne livre que celle de l'appareil.
