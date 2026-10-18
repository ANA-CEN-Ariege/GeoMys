# Mesure mémoire de TaxRef (lot 0) : bilan

*18 octobre 2026. Samsung Galaxy A12 (Android 12, MediaTek Helio P35, 4 Go de RAM) et iPhone 16 (iOS 27.2, 8 Go). Builds optimisés : type `mesure` sur Android (la release, non débogable, sans R8), configuration Release sur iOS.*

L'inventaire classait la mémoire sur iOS parmi les points durs (`INVENTAIRE.md` § 8.1, point 7). Les index TaxRef montent à 100 000 taxons et plus, iOS n'a pas d'équivalent à `largeHeap`, et GeoMys-IOS a déjà observé un pic de plus de 200 Mo. La décision 8 (§ 11) était de rester en JSON, et de passer à SQLite seulement si une mesure sur iPhone l'imposait.

**Verdict : la mémoire n'impose pas SQLite.** TaxRef reste en fichiers JSON chargés en mémoire, comme dans GeoMys-Android.
- Sur l'iPhone 16, le pic d'un référentiel de 100 000 taxons occupe 8 % de ce qu'iOS accorde à l'appli, et 17 % pour 200 000 taxons.
- Il faut deux conditions, toutes deux côté Android et déjà remplies par GeoMys-Android :
  - **déclarer `largeHeap`** : sans lui, 200 000 taxons occupent jusqu'à 99 % du tas de l'A12 ;
  - **charger TaxRef en arrière-plan et à la demande** : il faut 10 à 18 s pour 100 000 taxons sur l'A12.

## 1. Résultats

Une mesure correspond à un démarrage à froid suivi d'une session de saisie sur un protocole. On relit les huit fichiers du référentiel, puis on construit tous les index que `TaxRefCache` et `TaxRefLocal` gardent en mémoire (§ 3). Mo = 2²⁰ octets.

**100 000 taxons** (128 062 noms, 22,1 Mo de fichiers : la taille de l'appareil de référence de GeoMys-Android). Deux mesures par ligne.

| Appareil | Lecture | Pic | Retenu | Durée | Pic / limite |
|---|---|---|---|---|---|
| Galaxy A12 | en flux | 131 Mo | 119,5 Mo | 18,0 s | 51 % de 256 Mo |
| Galaxy A12 | en chaînes | 160 et 167 Mo | 119,5 Mo | 10,5 s | 62 et 65 % |
| iPhone 16 | en flux | 274 et 275 Mo | 148 et 143 Mo | 1,3 s | 8 % de 3 376 Mo |
| iPhone 16 | en chaînes | 277 et 260 Mo | 146 et 173 Mo | 0,54 s | 8 % |

**200 000 taxons** (256 316 noms, 44,7 Mo de fichiers). Une mesure par ligne.

| Appareil | Lecture | Pic | Retenu | Durée | Pic / limite |
|---|---|---|---|---|---|
| Galaxy A12 | en flux | 239 Mo | 224 Mo | 34,4 s | **93 %** de 256 Mo |
| Galaxy A12 | en chaînes | 253 Mo | 224 Mo | 23,2 s | **99 %** |
| iPhone 16 | en flux | 572 Mo | 306 Mo | 2,6 s | 17 % de 3 376 Mo |
| iPhone 16 | en chaînes | 511 Mo | 306 Mo | 1,0 s | 15 % |

Ce que mesure chaque colonne :
- **Pic** et **retenu** sont comptés au-dessus de l'occupation de départ de l'appli. Le pic est relevé toutes les 5 ms pendant le chargement ; le retenu l'est après deux passages du ramasse-miettes, index construits.
- **Limite** :
  - Android : le tas Java maximal (`maxMemory`). GeoMys Common ne déclare pas encore `largeHeap`, d'où 256 Mo sur l'A12 ; avec `largeHeap`, ce serait 512 Mo (`dalvik.vm.heapsize`).
  - iOS : l'empreinte au-delà de laquelle le système ferme l'appli, c'est-à-dire l'occupation plus `os_proc_available_memory`.
- **Préparation** (la synchronisation simulée, qui écrit les huit fichiers) : 6,0 s pour 100 000 taxons et 11,5 s pour 200 000 sur l'A12 ; 0,4 s et 0,8 s sur l'iPhone.

## 2. Ce qu'on en tire

**iOS.** La marge est large. Le pic vaut à peu près deux fois le retenu, en flux comme en chaînes, sans doute parce que le ramasse-miettes de Kotlin/Native laisse s'accumuler les déchets du chargement avant de les ramasser. C'est cohérent avec le pic de plus de 200 Mo observé par GeoMys-IOS. La limite est plus basse sur les iPhone qui ont moins de RAM. L'écran de mesure l'affiche pour l'appareil sur lequel il tourne.

**Android.**
- Le retenu double avec le référentiel : 119,5 Mo pour 100 000 taxons, 224 Mo pour 200 000.
- Sans `largeHeap`, 100 000 taxons tiennent mais prennent déjà la moitié ou les deux tiers du tas, avant le reste de l'appli. À 200 000, on frôle le dépassement mémoire (OOM).
- GeoMys Common devra donc déclarer `largeHeap`, comme GeoMys-Android, au plus tard au portage de TaxRef (lot 2).

**Durées.**
- L'A12 est 13 à 23 fois plus lent que l'iPhone 16 pour le même travail. C'est de l'ordre de l'écart de puissance entre un processeur d'entrée de gamme de 2020 et celui d'un iPhone récent.
- Sur un téléphone de ce niveau, le chargement complet doit donc se faire en arrière-plan et à la demande, jamais sur le fil de l'interface. GeoMys-Android le fait déjà : il ne construit ses index qu'au premier besoin, et les préchauffe hors du fil principal.
- Les builds débogables, plus lents sur Android, ne sont pas représentatifs.

**En flux ou en chaînes.**
- La lecture en flux, via okio et kotlinx-serialization-json-okio, est 1,7 à 2,4 fois plus lente que le décodage d'une chaîne, sur les deux téléphones.
- Elle ne réduit le pic que sur Android : 131 Mo contre 160 à 167 Mo pour 100 000 taxons. Sur iOS, elle ne le réduit pas.
- Le choix se fera au lot 2. On peut par exemple faire comme GeoMys-Android : le flux pour `cache_v3.json` seulement, le gros fichier, et les chaînes pour les sept autres.

**SQLite** reste le recours si des référentiels de plus de 200 000 taxons deviennent courants, ou si le chargement reste trop long sur les téléphones d'entrée de gamme, même en arrière-plan. Ce serait alors une réécriture de `TaxRefCache` et `TaxRefLocal`, et non plus un portage.

## 3. Méthode

**Ce qui est chargé.** Ce sont les huit fichiers de `filesDir/taxref/` de GeoMys-Android, avec les mêmes types :
- `cache_v3.json` : nom normalisé → entrée ;
- groupes, groupes1, règnes, index par taxon, listes, noms français et noms scientifiques par cd_nom.

On y ajoute leurs index dérivés, mémoïsés par `TaxRefCache` :
- entrées par cd_nom ;
- index des mots ;
- tous les noms ;
- cd_nom et noms de la liste du protocole ;
- index de résolution de cette liste, vide au-delà de 60 000 taxons comme dans GeoMys-Android ;
- les propositions de `TaxRefLocal` pour tous les groupes de la liste.

Le code est dans `core/…/outils/MesureMemoireTaxRef.kt`. Il reprend les structures de `TaxRefCache.kt` (GeoMys-Android @ `cdd5196`), décodées par kotlinx-serialization comme le fera le portage.

**Les données sont synthétiques**, calées sur l'appareil de référence de GeoMys-Android : synchronisé le 17 octobre, 100 468 taxons, une liste qui couvre tout, des protocoles jusqu'à 45 000 taxons. Le générateur suit ces hypothèses :
- noms scientifiques sans auteur, d'environ 20 caractères ;
- un nom français pour un taxon sur quatre environ ;
- 1,5 liste par taxon ;
- six espèces par genre, une épithète pour trois taxons.

Pour 100 000 taxons, les fichiers produits ont la taille des vrais à quelques pour cent près :

| Fichier | Réel (Mo décimaux) | Synthétique | Écart |
|---|---|---|---|
| `cache_v3.json` | 10,13 | 10,16 | +0 % |
| `groupes1_v1.json` | 2,37 | 2,38 | +0 % |
| `groupes_v1.json` | 2,03 | 2,18 | +7 % |
| `index_taxon_v1.json` | 0,70 | 0,70 | −0 % |
| `listes_v1.json` | 1,73 | 1,72 | −1 % |
| `regnes_v1.json` | 1,88 | 1,95 | +3 % |
| `sci_v1.json` | 3,18 | 3,11 | −2 % |
| `verns_v1.json` | 0,90 | 0,93 | +4 % |
| **total** | **22,93** | **23,14** | **+1 %** |

Le générateur est déterministe : il donne le même référentiel sur les deux plateformes, soit 128 062 noms pour 100 000 taxons.

**Ce qui est compté.**
- Android : le tas Java occupé (`totalMemory − freeMemory`), c'est-à-dire ce qui mène à l'OOM.
- iOS : l'empreinte physique du processus (`phys_footprint`), c'est-à-dire ce que surveille le système pour fermer une appli.
- Ces fonctions sont dans `core/…/plateforme/Memoire*.kt`. `os_proc_available_memory` passe par une petite définition cinterop (`core/src/nativeInterop/cinterop/proc.def`).

**Une mesure par lancement de l'appli.** Sur iOS, la mémoire rendue par le ramasse-miettes reste souvent attribuée au processus, et fausserait une seconde mesure faite dans le même processus.

## 4. Limites

- **Données synthétiques.** Les tailles de fichiers sont calées sur le réel, mais pas la répétition des mots dans les noms, qui dimensionne l'index des mots : c'est une hypothèse. Une mesure sur le vrai référentiel lèverait le doute, en copiant en lecture seule les fichiers de GeoMys-Android depuis l'A12. Claude Code a bloqué cette copie, qui touche aux données privées d'une autre appli ; elle reste possible si on l'autorise.
- **Pic sur Android.** Il compte aussi les déchets pas encore ramassés : c'est un majorant.
- **Retenu sur iOS.** Il varie d'une mesure à l'autre, de 143 à 173 Mo pour 100 000 taxons, selon ce que l'allocateur a gardé.
- **200 000 taxons.** La liste du protocole, à 45 %, dépasse alors le seuil de 60 000 taxons : son index de résolution n'est pas construit, comme dans GeoMys-Android.
- **Deux appareils seulement.**

## 5. Reproduire la mesure

À la main : sur l'accueil, bouton « Mesure mémoire TaxRef ».
1. Choisir le nombre de taxons et écrire les fichiers.
2. Relancer l'appli, puis lancer une mesure (en flux ou en chaînes).
3. Relancer l'appli avant chaque nouvelle mesure.

Les fichiers sont écrits dans le cache de l'appli sur Android, et dans son `tmp/` sur iOS.

En ligne de commande, chaque étape est un lancement de l'appli. Les résultats arrivent sur une ligne `MESURE_TAXREF` du journal.

**Android**, résultats lus dans `adb logcat -s System.out:I` :
```
./gradlew :androidApp:assembleMesure
adb install -r androidApp/build/outputs/apk/mesure/androidApp-mesure.apk
adb shell am start -n fr.ariegenature.geomys.common/.MainActivity --es ecran mesure-taxref --es action preparer --es taxons 100000
adb shell am force-stop fr.ariegenature.geomys.common
adb shell am start -n fr.ariegenature.geomys.common/.MainActivity --es ecran mesure-taxref --es action mesurer --es lecture flux
```

**iOS**, résultats affichés par `--console`. Les arguments de l'appli viennent après `--` :
```
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp -configuration Release -destination 'id=<UDID>' -allowProvisioningUpdates build
xcrun devicectl device install app --device <UDID> <DerivedData>/Build/Products/Release-iphoneos/GeoMysCommon.app
xcrun devicectl device process launch --console --terminate-existing --device <UDID> \
  fr.ariegenature.geomys.common -- -ecran mesure-taxref -action mesurer -lecture chaine
```

## 6. Ce qui reste de la mesure dans le code

Tout est provisoire. On peut le garder pour remesurer au lot 2, sur le vrai portage de TaxRef, puis le retirer :
- `core/…/outils/MesureMemoireTaxRef.kt` ;
- `ui/…/mesure/MesureTaxRefScreen.kt` et son bouton sur l'accueil ;
- le type de build `mesure` (`androidApp/build.gradle.kts`).

`core/…/plateforme/Memoire*.kt` pourra servir aux diagnostics de l'appli.

La mesure a fait entrer deux dépendances, de toute façon prévues au lot 1 : okio 3.18.2 et kotlinx-serialization-json-okio 1.11.0.
