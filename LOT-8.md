# Lot 8 : OccHab

*19 octobre 2026. Portage depuis GeoMys-Android `cdd5196` ; ses commits suivants ne touchent pas à OccHab, et les deux derniers (`e69543d`, `5b981f3`), sur les Suivis, sont reportés ici (§ 7). Allure iOS reprise de GeoMys-IOS `5fda84c`. La logique est dans `core`, les écrans dans `ui`.*

Le lot 8 porte les écrans d'OccHab (`INVENTAIRE.md` § 10) :
- la **carte OccHab** : consultation des stations (celles de l'appareil en rouge, celles déjà sur GeoNature en violet), création et réédition d'une géométrie (point, ou polygone et ses trous), aimantage aux stations voisines, import d'une station du serveur, fin de saisie et envoi ;
- les **informations obligatoires** d'une nouvelle station et les **détails** d'une station, dont la section ANA-EVAL ;
- l'**écran d'une station** : résumé de la géométrie et du jeu de données, habitats, « Détails », coche « Terminer » ;
- le **formulaire d'un habitat** : recherche HABREF, nomenclatures, « Autre, préciser », section ANA-EVAL ;
- **Mes stations** : les saisies OccHab de l'appareil, leur envoi, leur réédition et leur suppression.

Les écrans suivent la décision 15 ([`LOT-5.md`](LOT-5.md) § 2) : l'allure de GeoMys-Android sous Android, celle de GeoMys-IOS sous iOS ; les éléments, les textes et les comportements de GeoMys-Android sur les deux.

**État.** Tout le contenu du lot est porté, ainsi que les deux corrections des Suivis arrivées entre-temps dans GeoMys-Android. Les défauts d'Android que GeoMys Common reproduisait sont corrigés, y compris ceux des lots 2, 6 et 7, et l'interrupteur « Afficher mes stations déjà sur GeoNature » est supprimé (§ 5). Les tests du socle passent de 941 à 987 sur la JVM, et de 931 à 977 sur le simulateur iOS ; ceux des écrans de 8 à 29 sur la JVM, et de 1 à 3 sur le simulateur iOS (§ 7).

**Critère de sortie** (recette terrain OccHab) : **à faire**. Les écrans ont été essayés sur l'émulateur (Pixel 10) et sur le simulateur (iPhone 17 Pro), avec des données d'essai. L'appli est installée sur le Galaxy A12 et l'iPhone 16 (§ 8).

## 1. Ce qui est porté

| GeoMys-Android | GeoMys-IOS (`5fda84c`) | GeoMys Common |
|---|---|---|
| `OccHabCarteFragment.kt`, `fragment_occhab_carte.xml`, `StationsSessionOverlay.kt`, `StationsServeurOverlay.kt`, `IconesEditionCarte.kt` | `OccHabCarteView.swift`, `OccHabMapView.swift` | `ui/…/occhab/CarteOccHabScreen.kt` et `CarteOccHabViewModel.kt` ; `core/…/occhab/CarteOccHab.kt` |
| `EditeurGeometrie.kt`, `PileAnnulationGeometrie.kt`, `util/TopologiePolygone.kt`, `airePolygoneM2` (`MapUtils.kt`) | `OccHabEditeurGeometrie.swift` | `core/…/occhab/GeometrieEdition.kt`, `TopologiePolygone.kt` ; `core/…/geo/AireGeodesique.kt`, `Emprise.kt` |
| `OccHabDetailsDialog.kt` (dialogue des détails, listes de nomenclatures, section ANA-EVAL) | `OccHabInfosStationSheet.swift`, `AnaEvalSectionView.swift` | `ui/…/occhab/DialogueDetailsStation.kt`, `SectionAnaEval.kt` ; `core/…/occhab/DetailsStation.kt`, `EditionAnaEval.kt`, `ListeNomenclatureOccHab.kt` |
| `OccHabStationFragment.kt`, `fragment_occhab_station.xml` | `OccHabStationView.swift` | `ui/…/occhab/StationOccHabScreen.kt` et `StationOccHabViewModel.kt` ; `core/…/occhab/StationOccHab.kt` |
| `OccHabHabitatFragment.kt`, `fragment_occhab_habitat.xml` | `OccHabHabitatView.swift` | `ui/…/occhab/HabitatOccHabScreen.kt` et `HabitatOccHabViewModel.kt` ; `core/…/occhab/HabitatOccHab.kt` |
| `OccHabStationsFragment.kt`, `fragment_occhab_stations.xml`, `item_occhab_station.xml` | `OccHabStationsView.swift` | `ui/…/occhab/MesStationsScreen.kt` et `MesStationsViewModel.kt` ; `MesStations` (`core/…/occhab/StationOccHab.kt`) |
| `OccHabViewModel.kt` : la session | `OccHabSessionModel.swift` | `core/…/occhab/SessionOccHab.kt` ; `ui/…/occhab/SessionOccHabViewModel.kt` |
| altitudes MNT et chargement des stations d'un jeu de données (`OccHabCarteFragment`, `StationsServeurOverlay`) | — | `core/…/occhab/ServeurOccHab.kt` |
| `nav_graph.xml` : les destinations d'OccHab | — | `ui/…/navigation/NavigationGeoMys.kt` (§ 3) |

Le modèle, le stockage, la synchro et l'envoi viennent des lots 1 à 3 (`OccHabStore`, `StationsServeurCache`, `OccHabApi`, `OccHabUpload`, `EnvoiOccHab`).

## 2. Deux allures

Ce que chaque plateforme fait elle-même :

| Élément | Android | iOS |
|---|---|---|
| Stations sur la carte | contour de 4 px ; sommets des stations rouges en disques à liseré blanc ; épingle pour une station ponctuelle | contour de 1,5 pt ; gouttes rouges ou violettes (`OccHabMapView`) |
| Géométrie éditée | bleue, contour de 4 px ; épingles rouges aux sommets | rouge, trait de 3 pt ; gouttes aux sommets |
| Informations obligatoires, détails | AlertDialog d'AppCompat à la vue construite par programme | feuille de GeoMys-IOS (« Annuler », titre, « Valider ») sur le dégradé, sections de `Form` |
| Dates | texte ouvrant `DatePickerDialog` | `UIDatePicker` compact, à droite de sa ligne |
| Formulaire d'un habitat | champs soulignés, `Spinner`, liste HABREF déroulée sous le champ ; barre du bas `colorSurface` | sections de `Form` (lignes #1C1C1E), menus du système, résultats HABREF en lignes sous le champ ; bouton vert pleine largeur au ras du bas |
| Mes stations | `item_occhab_station` : icônes teintées (envoi, édition, suppression) | `OccHabStationsView` : symboles SF `paperplane.fill`, `pencil`, `trash` |
| Retour | bouton ou geste Retour | balayage depuis le bord sur la station et l'habitat (§ 5) ; désactivé sur la carte, comme GeoMys-IOS |

**Composants nouveaux ou étendus** (`ui/…/composants`, `ui/…/carte`) :
- `ChampJour` (une date seule) ; `ChampsListes` (liste à filtre, observateurs, menus iOS) : sortis du dialogue des détails d'un relevé (lot 6) pour servir aussi aux détails d'une station ;
- `LignesFormulaireIos` (lignes de `Form` : choix, texte, nombre, texte seul ; le libellé au-dessus du champ, § 5) ;
- `Interrupteur` gagne `fondIos`, la couleur de la ligne qui le porte, peinte derrière l'`UISwitch` ; `FeuilleIos`, `SectionIos`, `DialogueFeuilleFormulaire` et `DialogueProgression` dans `Dialogues` ;
- `CarteGeoMys` gagne `onAvantClic` (un toucher traité avant les couches : l'aimantage) ; `ElementsCarte`, les sommets déplaçables et la goutte de GeoMys-IOS.

## 3. Navigation

- **Routes typées.** `RouteOccHabCarte`, `RouteOccHabStation`, `RouteOccHabHabitat(habitatId)`, `RouteMesStations`. La session OccHab (`SessionOccHabViewModel`) vit au-dessus du graphe, comme l'`OccHabViewModel` d'activité d'Android. `RouteAVenir` ne sert plus qu'à l'Explorer et à Maps Manager (lot 9).
- **Enchaînements** (`nav_graph.xml`) : la carte empile la station, la station empile l'habitat ; l'habitat validé rend la main à la station ; « Terminer » ouvre une carte vierge, la pile ramenée à l'accueil ; « Mes stations » empile la carte de la saisie reprise.
- **Accueil.** « OccHab » démarre une nouvelle session ; le menu « Mes stations » ouvre la liste.

## 4. La logique sort des écrans

Elle vivait dans les Fragments et leurs overlays ; elle devient des objets du socle, testés sans écran :
- `GeometrieEdition` : la géométrie en cours, valeur immuable que chaque geste remplace (tap, sommet déplacé ou inséré, trous), et sa pile d'annulation ;
- `SessionOccHab` : la saisie, la station courante, les détails par station, l'empreinte d'origine d'une station importée ;
- `CarteOccHab` : ce qui est dessiné, le classement des stations du serveur, les cibles d'aimantage, le cadrage, les textes et la fin de saisie ;
- `DetailsStation`, `HabitatOccHab`, `EditionAnaEval`, `StationOccHab` et `MesStations` : champs, règles et textes des formulaires et des listes ;
- `ServeurOccHab` : altitudes MNT d'une géométrie, chargement des stations d'un jeu de données ; `AireGeodesique`, `Emprise`.

L'automate de la carte (consultation, saisie, sélection, annulation) reste dans `CarteOccHabViewModel`, testé sur la JVM (§ 7).

## 5. Écarts assumés

**Divergence du § 8.3 de l'inventaire** : pendant le dessin d'une nouvelle géométrie, un toucher à moins de 44 dp d'un sommet voisin s'y aimante, même sur une station voisine, comme le propose l'inventaire (GeoMys-IOS). Sous Android, la sélection de la voisine l'emportait et le tracé était abandonné.

**Défauts d'Android corrigés** (inventaire, § 8.2). La décision 5 est tranchée le 19 octobre : GeoMys Common corrige les défauts de GeoMys-Android qu'il reproduisait. Pour OccHab :
- l'écran d'une station s'intitule « Nouvelle station » quand sa géométrie vient d'être dessinée, « Modifier la station » quand elle est reprise de la saisie, d'une autre saisie ou du serveur. Sous Android, la carte enregistrait la station avant d'ouvrir son écran, qui s'intitulait donc toujours « Modifier la station » (n° 16) ;
- l'interrupteur « Afficher mes stations déjà sur GeoNature (ce jeu de données) » est supprimé des informations obligatoires et des détails (décision du 19 octobre) : les stations du serveur s'affichent toujours. Sous Android, « Annuler » qui quittait une saisie les réaffichait toutes, même interrupteur décoché (n° 18) ;
- une station à éditer qui s'avère déjà envoyée (la copie locale d'une autre saisie, envoyée entre-temps) ne fait plus passer la carte en saisie avant la question « Modifier cette station déjà envoyée ? » : refusée, la carte reste en consultation. Sous Android, la carte restait en saisie sans géométrie : un tap commençait une station sans ses informations obligatoires, et si la saisie rouverte n'avait que cette station, le tracé validé remplaçait sa géométrie (n° 19).

Et ceux des lots précédents :
- OccTax (lot 6) : la date des « Détails communs » n'est retenue que modifiée, comme sous GeoMys-IOS ; elle arme alors la saisie a posteriori, et l'heure d'ouverture du dialogue ne s'impose plus aux relevés suivants (n° 4). Le total d'individus compte tous les dénombrements, dans les listes comme dans le relevé (n° 5). « Continuer la saisie » rouvre en mono une saisie faite en mono, avec les champs additionnels de son dernier relevé ; les saisies enregistrées avant la correction n'ont pas retenu leur mode et se rouvrent en multi (n° 7) ;
- synchro (lot 2) : hors ligne, l'échec de la connexion qui précède la lecture de la configuration Occtax n'interrompt plus « Recharger les données ». Chaque étape échoue à son tour sans rien effacer, et le compte-rendu « ⚠ Chargement incomplet » les énumère (n° 6) ;
- Suivis (lot 7) : deux écritures simultanées d'une même clé du cache Monitoring se suivent, au lieu de partager leur fichier temporaire (n° 11).

**Défaut d'Android non reproduit :** le bandeau ne parle plus de stations « orange = déjà reprise dans une autre saisie », que plus rien ne dessine en orange depuis le 18 octobre (n° 17).

**Pas repris :**
- `memePoint` de `TopologiePolygone`, resté sans appelant depuis le retrait de la topologie partagée ;
- de GeoMys-IOS, ce qu'Android n'a pas : « Référentiel des habitats absent du cache… », le contrôle des entiers ANA dans le formulaire d'un habitat (aucun champ entier côté habitat), le masquage des erreurs pendant un envoi dans « Mes stations », les résultats HABREF toujours affichés (ils se déroulent ici tant que le champ a le focus, comme sous Android), le fil d'Ariane cliquable.

**Comportements d'Android sous iOS :**
- le libellé d'un champ au-dessus de lui, sur sa propre ligne, et non à côté comme dans les formulaires de GeoMys-IOS (demande du 19 octobre) : lignes de choix et de nombre (détails d'une station, ANA-EVAL), et cases à cocher des formulaires des Suivis (lot 7) ;
- les textes d'Android, dont les informations obligatoires (champs séparés « Date de début », « Date de fin ») et « Ajouter un habitat » ;
- la coche « Terminer » toujours visible ; le « i » des détails présent pendant toute la saisie, actif seulement sur une station sélectionnée ;
- « Annuler » au départ d'une saisie l'abandonne (GeoMys-IOS n'y avait rien à annuler) ; une station sélectionnée se quitte par « Annuler » avant d'en prendre une autre.

**Choix du portage :**
- **Retour sous iOS.** Le bandeau d'Android (« OccHab ») n'a pas les niveaux cliquables du fil de GeoMys-IOS : le balayage depuis le bord tient lieu du bouton Retour sur la station (vers la carte) et sur l'habitat (vers la station, sans l'enregistrer). Il reste désactivé sur la carte, où il gênerait les gestes.
- **Mort du processus (Android).** La garde d'Android est reprise : la station ou l'habitat rouvert sur une session vide renvoie à l'accueil (« Saisie interrompue… »), au lieu d'enregistrer une station fantôme en (0, 0).
- **Libellés des formulaires sous Android** : 14 sp gras, la taille par défaut d'un TextView du thème, que le code d'Android ne fixe pas.

## 6. Données d'essai

`action=occhab-essai` (émulateur et simulateur seulement) écrit, comme après une synchro :
- deux jeux de données OccHab (« Cartographie des habitats du Plantaurel », « Tourbières de l'Ariège »), des observateurs s'il n'y en a pas, les nomenclatures et les défauts d'OccHab, six habitats HABREF dans le cache d'OccHab ;
- quatre stations « déjà sur GeoNature », au-dessus de Foix : une prairie à trou portant un bloc ANA-EVAL, une source ponctuelle, une lande en deux parties (non modifiable dans l'appli), une tourbière d'un autre jeu de données ;
- une saisie à envoyer (une pelouse mitoyenne de la prairie, un point sans habitat) et une saisie envoyée.

Toutes ces données sont fictives.

## 7. Tests

**Socle : 46 cas de plus**, sur la JVM et le simulateur iOS : 41 pour OccHab, 1 pour le suivi de GeoMys-Android, 4 pour les défauts corrigés. **Écrans : 23 cas de plus** : sur la JVM, 17 pour OccHab, 1 pour le suivi, 3 pour les défauts corrigés ; sur le simulateur iOS, 2 pour les lignes de formulaire de l'allure iOS.

| Origine | Cas | Fichiers |
|---|---|---|
| Portés de GeoMys-Android | 16 | `SessionOccHabTest` (6 sur les 9 d'`OccHabViewModelTest` : les cas de l'interrupteur supprimé ne sont pas repris), `TopologiePolygoneTest` (3 sur 4 : le cas de `memePoint` n'est pas repris), `OccHabPolygoneTrouTest` (les 4 derniers cas, avec l'éditeur ; le fichier est complet), `IncertitudeEnvoiOccHabTest` (3 : les cas d'A12 de `DefautsPariteLot2Test`) |
| Gardes textuelles réécrites en tests de comportement | 5 | `GeometrieEditionTest` (2 : `OccHabSommetAimanteIndependantTest`), `CarteOccHabTest` (1) et `CarteOccHabEmpriseTest` (2, écrans) : `OccHabEmpriseStableTest` ; la garde d'A12 sur `EnvoiOccHab` devient un envoi actionné |
| Sur le modèle d'`OccHabEditeurTests` de GeoMys-IOS | 15 | `EditeurCarteOccHabTest` (écrans) : consultation et saisie, point et polygone, verrou du type, annulation, sélection, glisser, insertion, trous, aimantage avant sélection ; adaptés au comportement d'Android |
| Écrits pour le portage | 24 | `GeometrieEditionTest` (6), `CarteOccHabTest` (6), `FormulairesOccHabTest` (10), `LignesFormulaireIosTest` (2, écrans, sur le simulateur iOS) |
| Défauts d'Android corrigés (§ 5) | 7 | `EditeurCarteOccHabTest` (3 de plus, écrans : n° 16 et 19), `ReleveEnCoursTest` (n° 4), `ListeObservationsTest` (n° 5), `SessionSaisieOcctaxTest` (n° 7), `MonitoringCacheEcrituresTest` (n° 11, sur le vrai système de fichiers, seize écritures à la fois) ; s'y ajoutent des cas existants adaptés : `SyncRunnerTest` (n° 6), `SaisieMonoTest` (n° 5), `FormulairesOccHabTest` (n° 16) |

Chaque cas des défauts corrigés échoue quand on réintroduit le défaut. Les tests d'écrans pilotent les ViewModels avec les vrais services sur un dossier temporaire, sans réseau. `EditeurCarteOccHabTest` fait avancer le fil principal à la main : les suites des coroutines y passent dans l'ordre de l'appli, et non sur les fils d'entrée-sortie.

**Suivi de GeoMys-Android** (INVENTAIRE § 12). Deux commits du 19 octobre corrigent les défauts n° 14 et 15 du lot 7 ; ils sont reportés :
- `e69543d` : un ajout de photos seul compte comme une modification. La lecture des valeurs rend une copie de la liste des photos ; le cas ajouté à `FormulaireRendererTest` est porté dans `FormulaireEtatTest`, et `ContratValeursTest` suit le nouveau contrat ;
- `5b981f3` : la ligne « Sur : … — Protocole : … » survit à « Enregistrer + suivante ». La garde d'Android sur le texte du Fragment devient un test de comportement (`LigneContexteNouvelleVisiteTest`, écrans) : une observation d'essai enregistrée en chaîne, la ligne du formulaire suivant vérifiée.

**Essais d'affichage**, jamais sur les téléphones :
- émulateur Pixel 10 :
  - carte : consultation, informations obligatoires (refus sans observateur), point posé et validé, sélection de la pelouse (poignées « + », « i » et ses détails), import de la prairie à trou, glisser d'un sommet du trou puis « Annuler », polygone tracé vite avec aimantage, « Terminer » ;
  - station : détails, habitat ajouté, rouvert, retour sans enregistrer ;
  - habitat : recherche HABREF, « Autre, préciser », recouvrement à virgule, champ vidé au second toucher ;
  - Mes stations : onglets, « Tout envoyer » affiché, envoi refusé faute de configuration (« Configuration requise »), dialogue de suppression, réédition ;
  - après les corrections : informations obligatoires et détails sans l'interrupteur, point dessiné dans la saisie reprise, dont l'écran s'intitule « Nouvelle station » ;
- simulateur iPhone 17 Pro : carte en consultation et en édition, feuilles des informations obligatoires et des détails, station, habitat (nouveau et existant), Mes stations ; les champs date des Suivis et des détails d'un relevé, avec une date d'un autre jour imposée le temps d'un essai.

**Corrigé en cours de lot.**
- Tracé vite, un polygone restait invisible : ses couches, ajoutées en cours de dessin (la ligne au deuxième sommet, la surface insérée sous elle au troisième), ne s'affichaient pas quand les touchers s'enchaînaient. Elles sont désormais toujours présentes, vides hors tracé.
- Sous iOS, le titre de la feuille des informations obligatoires était tronqué.
- Sous iOS encore, une date s'affichait tantôt « 28/05/2026 », tantôt « 19 oct. 2026 », selon le jour. Le `UIDatePicker` compact n'a pas de taille intrinsèque. À sa taille compressée, un tiers de point plus étroite que la pastille de « 28 mai 2026 », il passait au format court, et sa pastille se décalait. Il prend maintenant toute la largeur offerte, sa pastille à droite dans les détails d'une station, à gauche dans les formulaires des Suivis (lot 7) et les détails d'un relevé (lot 6), qui avaient le même défaut.
- Relevé sur l'iPhone 16, dans les détails d'une station : une valeur longue (« Calculée à partir de la géométrie ») réduisait le libellé « Méthode de calcul de la surface », placé à côté d'elle, à une lettre par ligne. Le libellé est désormais au-dessus (§ 5), et une valeur trop longue pour sa ligne finit en « … » (`LignesFormulaireIosTest`).
- Sous iOS, dans les formulaires des Suivis (lot 7), la case à cocher éteinte laissait voir le dégradé de l'écran à travers sa piste translucide et dans ses coins : un rectangle bleuté sur la ligne grise. Elle est maintenant peinte sur la couleur de la ligne (`fondIos`). Les champs additionnels OccTax ont le même interrupteur sur d'autres fonds ; ils restent à vérifier.
- Sous iOS toujours, tous les interrupteurs (accueil, saisies, Suivis) avaient le bout droit rogné depuis le lot 5 : l'`UISwitch` annonce une taille intrinsèque de 61 pt de large mais se dessine sur 63 pt. La vue prend maintenant la taille que donne `sizeThatFits`.

## 8. Critère de sortie : la recette terrain

À faire sur le Galaxy A12 et l'iPhone 16, avec la configuration et les données chargées aux lots 2 et 5, sur un jeu de données OccHab réel. L'utilisateur saisit lui-même ses identifiants ; aucune donnée d'essai n'est chargée sur les téléphones.

1. **Nouvelle station** : « OccHab », informations obligatoires, point puis polygone ; aimantage sur une station voisine ; « Valider », détails (altitudes MNT remplies avec du réseau), habitats.
2. **Réédition** : une station de la saisie (glisser, « + », « Annuler »), une station du serveur importée, une station à trou si le serveur en a.
3. **Fin de saisie** : coche « Terminer », « Enregistrer et envoyer » ; puis « Mes stations », « Tout envoyer ».
4. **Côté serveur** : stations, habitats et blocs ANA-EVAL dans GeoNature, sans doublon ; une station importée repart en mise à jour.
5. **Points à éprouver** : les corrections des défauts n° 16 et 19, les stations du serveur toujours affichées (§ 5) ; sous iOS, les libellés au-dessus des champs et le balayage de retour ; la recherche HABREF hors ligne ; la virgule du recouvrement sur le clavier Samsung et sous iOS.

## 9. Reste à faire

- **La recette terrain** (§ 8), avec celles des lots 6 et 7. S'y éprouvent aussi les corrections des défauts n° 4, 5 et 7 (OccTax) et n° 6 (synchro hors ligne, § 5).
- **À trancher** : le nord de la boussole (lot 6) et les sujets Monitoring du lot 7 (inventaire, § 8.3) ; la mort du processus sous Android (lots 6 et 7).
- **À vérifier sous iOS** : l'interrupteur des champs additionnels OccTax, sur le fond de chaque écran qui les montre (§ 7).
- **Lot 9** : l'Explorer, Maps Manager, les MBTiles et le téléchargement de zones.

## 10. Outils

- **Paramètres de lancement** (essais d'affichage) :
  - `action=occhab-essai` (§ 6) ;
  - `ecran=` : `occhab-carte` (nouvelle session), `occhab-infos` (ses informations obligatoires ouvertes), `occhab-reprise` (la saisie d'essai reprise), `occhab-edition` (sa pelouse sélectionnée), `occhab-station`, `occhab-details` (détails ouverts), `occhab-habitat`, `occhab-habitat-nouveau`, `mes-stations`.
