# Mode pro et mode simplifié : les différences

État au 15 novembre 2026 (version 2.2.0-beta3, build 207).

**À tenir à jour.** Toute modification qui fait diverger ou converger les deux modes se reporte ici dans le même commit. Les divergences se retrouvent dans le code par `ModeSaisie`, `simplifie`, `occtaxSeul` et `donneesSimplifiees` ; le détail des décisions est dans [`LOT-9.md`](LOT-9.md) § 43 à 49.

## En bref

| | Mode pro | Mode simplifié |
|---|---|---|
| Public | Naturalistes habitués à GeoNature | Utilisateurs amateurs |
| Modules | Saisie multi-taxons, saisie mono-taxon, Monitoring, OccHab | Saisie multi-taxons seulement |
| Champs de l'observation | Tous ceux que le serveur montre | Nombre, stade de vie, sexe, état biologique, comportement, commentaire, photo, audio |
| Données chargées | Tout | Seulement ce qui sert à la saisie multi-taxons |

Le mode pro est le mode par défaut : sans choix, et après une mise à jour, rien ne change.

## Le choix du mode

- Il se fait en tête des Paramètres, dans la carte « Mode de saisie » (Pro ou Simplifié).
- Il est enregistré aussitôt, dans les préférences de l'appli (`PreferencesGeoMys.modeSaisie`) et non dans la configuration du serveur : ni un changement de serveur ou de compte, ni « Vider le cache » n'y touchent.
- Le mode est lu à l'ouverture d'un relevé : un relevé ouvert garde le mode qu'il avait.

## Accueil

| | Mode pro | Mode simplifié |
|---|---|---|
| Boutons de saisie | « Saisie multi-taxons » et « Saisie mono-taxon » | Un seul, « Démarrer une saisie » (ouvre la saisie multi-taxons), plus haut, avec un « + » |
| Configuration incomplète | Boutons estompés | Bouton estompé et, dessous, « Pour commencer, renseignez la connexion dans les Paramètres », qui ouvre les Paramètres |
| « Monitoring » | Si des protocoles sont chargés | Jamais |
| « OccHab » | Si le serveur l'a et que l'utilisateur peut créer | Jamais |
| Menu : « Mes saisies », « Explorer », « Maps Manager » | Oui | Oui |
| Menu : « Mes visites », « Mes stations » | Si le module est là, ou s'il reste quelque chose à envoyer | Seulement s'il reste quelque chose à envoyer |

## Saisie multi-taxons

La carte, la trace GPS, le placement des observations, les détails du relevé (dont son commentaire), « Mes saisies » et l'envoi sont **identiques** dans les deux modes. Seule la saisie d'une espèce diffère.

### Après l'ajout d'une espèce

| | Mode pro | Mode simplifié |
|---|---|---|
| Ce qui s'ouvre | Rien, sauf cas ci-dessous : on reste sur le relevé | La « Fiche de l'observation », à chaque espèce |
| Champs additionnels obligatoires vides | La Caractérisation ou le Dénombrement s'ouvre d'office | « Détails » s'ouvre par-dessus la fiche |
| Oiseau en période de nidification | La Caractérisation s'ouvre, réduite au comportement | « Comportement » s'ouvre par-dessus la fiche |

### La ligne d'une espèce dans le relevé

| | Mode pro | Mode simplifié |
|---|---|---|
| Boutons | « × N » (Dénombrement) et « i » (Caractérisation) | Un crayon, qui ouvre la fiche ; toucher le nom l'ouvre aussi |
| Champ obligatoire vide | Le bouton de l'écran concerné est rouge | Le crayon est rouge |
| Corbeille | Oui, avec confirmation | Oui, avec confirmation |

### Les écrans de l'espèce

**Mode pro**, comme le web de GeoNature :

- **Dénombrement** : objet et type du dénombrement, nombre min et nombre max, stade de vie, sexe, médias, champs additionnels ; plusieurs dénombrements par espèce.
- **Caractérisation** : technique d'observation, état biologique ; sous « Plus de champs » : méthode de détermination, statut d'observation, naturalité, statut biologique, comportement, statut de la source, floutage, preuve d'existence, preuves numérique et non numérique, déterminateur, commentaire ; champs additionnels.

**Mode simplifié**, la « Fiche de l'observation » et ses cinq boutons :

| Bouton | Ce qu'il porte | Présent si |
|---|---|---|
| Détails | Nombre, stade de vie, sexe, état biologique, plus les champs additionnels obligatoires | Toujours |
| Comportement | Le comportement | Le serveur montre `behaviour` et l'espèce est un animal |
| Commentaire | Le commentaire de l'observation | Le serveur montre `comment_occ` |
| Photo | Appareil photo ou galerie | Le serveur active les médias |
| Audio | Choix d'un fichier son | Le serveur active les médias |

La fiche n'a rien à valider : chaque volet validé et chaque média sont écrits aussitôt dans le relevé.

### Règles propres au mode simplifié

- **Un seul nombre.** Il vaut pour le minimum et le maximum du premier dénombrement. Un maximum saisi en mode pro reste tant que le nombre ne change pas.
- **Un seul dénombrement.** La fiche ne touche que le premier ; les autres, saisis en mode pro, sont conservés tels quels.
- **Champs selon le groupe taxonomique.** Le sexe et le comportement ne sont proposés que pour un animal. Une nomenclature sans aucune valeur pour le taxon est masquée. En mode pro, tous les champs restent, comme sur le web.
- **Champs non montrés.** Ils gardent leur valeur ; jamais saisis, ils restent vides et le serveur y met sa valeur par défaut (technique d'observation, objet du dénombrement, etc.).
- **Champs additionnels facultatifs.** Ils ne sont pas proposés ; seuls les obligatoires le sont, dans « Détails ».
- **Observation déjà envoyée.** « Photo » et « Audio » sont grisés, avec une phrase d'explication.

## Paramètres et chargement des données

| | Mode pro | Mode simplifié |
|---|---|---|
| « Charger les données » | Tout : OccTax, Suivis, OccHab | OccTax seulement |
| Suivis (modules, schémas, objets, droits, listes de taxons des protocoles) | Chargés | Non chargés |
| OccHab (jeux, habitats, configuration, défauts, stations) | Chargé | Non chargé |
| TaxRef | Listes d'OccTax et des protocoles | Listes d'OccTax seulement |
| Résumé du chargement | Compte aussi les protocoles et les stations OccHab | Ne les compte pas |

Chargés dans les deux modes : jeux de données, listes, observateurs, champs additionnels et configuration d'OccTax, habitats d'OccTax, nomenclatures, détection des modules.

Le reste des Paramètres (connexion, liste des serveurs, sélections par défaut) est identique.

## Passer d'un mode à l'autre

- **Pro vers simplifié.** Rien n'est effacé : les caches des Suivis et d'OccHab restent sur le téléphone, et les visites ou stations en attente restent accessibles par le menu.
- **Simplifié vers pro.** Si les données ont été chargées en mode simplifié, Monitoring et OccHab restent masqués à l'accueil, et le bandeau des Paramètres invite à recharger : « Données chargées pour la saisie simplifiée : rechargez-les pour retrouver Monitoring et OccHab. La saisie multi-taxons reste utilisable d'ici là. » Un rechargement en mode pro les rend.
- **Saisies faites dans un mode, rouvertes dans l'autre.** Elles s'ouvrent sans perte : le mode simplifié ne modifie que les champs qu'il montre.

## Ce qui ne dépend pas du mode

Saisie mono-taxon, Monitoring et OccHab (absents du mode simplifié, inchangés en mode pro), Explorer, Maps Manager, carte et trace GPS, envoi des saisies, connexion au serveur.
