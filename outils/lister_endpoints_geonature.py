#!/usr/bin/env python3
# GeoMys-Common — application de saisie naturaliste pour GeoNature, Android et iOS.
# Copyright (C) 2026 ANA - CEN Ariège
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

"""Les routes GeoNature et TaxHub qu'appelle GeoMys Common, relatives à la racine du serveur
GeoNature (`/api/...`).

Repris de GeoMys-Android @ cdd5196 (tools/lister_endpoints_geonature.py) et mis à jour le 21 octobre
2026 d'après le code de GeoMys Common (OccTax, OccHab, Suivis, Explorer). Aucune requête réseau : la
liste sert à documenter la surface d'API ou à régler un pare-feu, un reverse-proxy, une liste
d'autorisations. Le détail (authentification, délais, fichiers) est dans
inventaire/1-reseau-sync.md, § 3.

Usage :
    outils/lister_endpoints_geonature.py            # chemins seuls, un par ligne
    outils/lister_endpoints_geonature.py --method   # méthode HTTP devant
    outils/lister_endpoints_geonature.py --query    # avec la chaîne de requête
    outils/lister_endpoints_geonature.py --full     # méthode, chemin, requête, accès et usage

Notes :
- TaxHub est servi par GeoNature ≥ 2.15 sous `<URL_GEONATURE>/api/taxhub`, d'où les chemins
  `/api/taxhub/api/...`. Si le serveur publie un autre `sync.taxhub_url` (réglages mobiles
  OCCTAX de t_mobile_apps), ces appels partent vers cette adresse.
- `<…>` : valeur posée à l'exécution (identifiant de liste, code de protocole, uuid…).
- Accès : « public » sans authentification, « connecté » avec le jeton et les cookies du login,
  « écriture » pour les appels qui créent, modifient ou suppriment des données.
"""
import sys

# (méthode, chemin, requête, accès, usage)
ENDPOINTS = [
    # Connexion et configuration
    ("POST", "/api/auth/login", "", "public", "Authentification : jeton JWT et cookies"),
    ("GET", "/api/gn_commons/config", "", "public",
     "Version de GeoNature ; formulaires OCCTAX et OCCHAB ; droits des Suivis ; filtre taxonomique"),
    ("GET", "/api/gn_commons/t_mobile_apps", "", "public",
     "Réglages mobiles OCCTAX : sync.taxhub_url, sync.page_size, area_observation_duration, input.date"),
    ("GET", "/api/gn_commons/modules", "", "connecté", "Droits (CRUVED) sur OCCTAX, OCCHAB et les Suivis"),
    # Synchro : référentiels et listes
    ("GET", "/api/meta/datasets", "active=true&module_code=<MODULE>&fields=modules", "connecté",
     "Jeux de données lisibles (OCCTAX, OCCHAB)"),
    ("POST", "/api/meta/datasets", "active=true  corps {\"create\":\"<MODULE>\"}", "connecté",
     "Jeux de données où l'on peut créer (filtre lu dans le corps JSON)"),
    ("GET", "/api/users/menu/<id>", "", "connecté", "Observateurs d'une liste (OccTax, OccHab, Suivis)"),
    ("GET", "/api/gn_commons/additional_fields", "module_code=OCCTAX", "connecté", "Champs additionnels d'OccTax"),
    ("GET", "/api/nomenclatures/nomenclatures/taxonomy", "", "connecté",
     "Nomenclatures et leurs filtres taxonomiques (replis : /api/nomenclatures/nomenclature/taxonomy, /api/nomenclatures/taxonomy)"),
    ("GET", "/api/nomenclatures/nomenclature/<TYPE>", "orderby=label_default", "connecté",
     "Valeurs d'une nomenclature (champs additionnels, listes des Suivis)"),
    ("GET", "/api/occtax/defaultNomenclatures", "organism=<id_organisme>", "connecté", "Valeurs par défaut d'OccTax"),
    ("GET", "/api/occhab/defaultNomenclatures", "", "connecté", "Valeurs par défaut d'OccHab"),
    ("GET", "/api/habref/habitats/autocomplete", "limit=200000[&id_list=<id>]", "public",
     "Référentiel HABREF complet, mis en cache (OccTax, OccHab)"),
    ("GET", "/api/taxhub/api/taxref/version", "", "public", "Version de TaxRef : saute le retéléchargement"),
    ("GET", "/api/taxhub/api/biblistes", "", "public", "Listes de taxons"),
    ("GET", "/api/taxhub/api/taxref", "orderby=cd_nom&fields=listes&id_liste=<id>&limit=<n>&page=<p>", "public",
     "Taxons d'une liste, page par page"),
    ("GET", "/api/monitorings/modules", "", "connecté", "Protocoles de suivi"),
    ("GET", "/api/monitorings/config/<module>", "", "connecté", "Schéma d'un protocole"),
    ("GET", "/api/monitorings/object/<module>/module", "depth=1", "connecté", "Enfants d'un protocole"),
    ("GET", "/api/monitorings/object/<module>/<type>/<id>", "depth=1", "connecté",
     "Fiche d'un objet de suivi et ses enfants (aussi l'anti-doublon de l'envoi)"),
    ("GET", "/api/<api d'un champ>", "", "connecté",
     "Options des listes des Suivis, déclarées par chaque protocole (meta/datasets, nomenclatures/nomenclature/<TYPE>, "
     "monitorings/list/<module>/<type>, users/menu/<id>, users/roles…)"),
    ("GET", "/api/media/monitorings/<module>/img.jpg", "", "connecté", "Pictogramme d'un protocole"),
    ("GET", "/api/occhab/stations/", "format=geojson&habitats=1&nomenclatures=1", "connecté",
     "Stations OccHab (l'appli ne garde que celles de l'utilisateur)"),
    # Envoi
    ("POST", "/api/occtax/OCCTAX/only/releve", "", "écriture", "Création d'un relevé"),
    ("POST", "/api/occtax/OCCTAX/releve/<id>/occurrence", "", "écriture", "Ajout d'une occurrence"),
    ("GET", "/api/occtax/OCCTAX/releves", "unique_id_sinp_grp=<uuid>&limit=1", "connecté", "Anti-doublon d'un relevé"),
    ("GET", "/api/occtax/OCCTAX/releve/<id>", "", "connecté", "Vérification d'un relevé avant un nouvel essai"),
    ("DELETE", "/api/occtax/OCCTAX/releve/<id>", "", "écriture", "Annulation d'un relevé incomplet"),
    ("GET", "/api/nomenclatures/nomenclature", "id_type=<id>&limit=200 (ou code_nomenclature_type=<TYPE>)", "connecté",
     "Repli à l'envoi si le cache des nomenclatures est vide (aussi /api/nomenclatures/nomenclatures)"),
    ("POST", "/api/geo/altitude", "", "connecté", "Altitudes d'un relevé OccTax, calculées par le serveur"),
    ("POST", "/api/geo/info", "", "connecté", "Altitudes d'une station OccHab"),
    ("GET", "/api/gn_commons/get_id_table_location/<schema.table>", "", "connecté", "Table de rattachement d'un média"),
    ("POST", "/api/gn_commons/media", "", "écriture", "Envoi d'un média (multipart)"),
    ("GET", "/api/gn_commons/medias/<uuid>", "", "connecté", "Médias déjà envoyés (anti-doublon)"),
    ("DELETE", "/api/gn_commons/media/<id>", "", "écriture", "Annulation d'un média"),
    ("POST", "/api/monitorings/object/<module>/<type>", "", "écriture", "Création d'une visite, d'une observation…"),
    ("POST", "/api/occhab/stations/", "", "écriture", "Création d'une station OccHab"),
    ("POST", "/api/occhab/stations/<id>/", "", "écriture", "Mise à jour d'une station OccHab"),
    ("GET", "/api/occhab/stations/<id>/", "", "connecté", "Version du serveur d'une station avant sa mise à jour"),
    ("GET", "/api/occhab/stations/", "format=json", "connecté", "Anti-doublon d'une station"),
    # Explorer
    ("POST", "/api/synthese/for_web", "limit=1000  corps {geoIntersection, date_min}", "connecté",
     "Observations autour de soi (filtres lus dans le corps JSON)"),
]


def main():
    args = set(sys.argv[1:])
    complet = "--full" in args
    methode = "--method" in args or complet
    requete = "--query" in args or complet
    vus = set()
    for verbe, chemin, req, acces, usage in ENDPOINTS:
        ligne = chemin + (f"?{req}" if requete and req else "")
        if methode:
            ligne = f"{verbe:6} {ligne}"
        if complet:
            ligne = f"{ligne}\n         [{acces}] {usage}"
        if ligne in vus:
            continue
        vus.add(ligne)
        print(ligne)


if __name__ == "__main__":
    main()
