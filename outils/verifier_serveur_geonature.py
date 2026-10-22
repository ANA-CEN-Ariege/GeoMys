#!/usr/bin/env python3
# GeoMys — application de saisie naturaliste pour GeoNature, Android et iOS.
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

"""Vérifie qu'un serveur GeoNature expose ce qu'utilise GeoMys, et donne pour chaque route :
OK (conforme), ATTENTION (repli ou dégradation, non bloquant) ou ÉCHEC (à examiner).

Repris de GeoMys-Android @ cdd5196 (tools/verifier_serveur_geonature.py) et étendu le 21 octobre 2026
à ce qu'appelle GeoMys : Suivis, OccHab, Explorer, et les lectures de l'envoi.

Usage :
    outils/verifier_serveur_geonature.py <url_geonature>            # identifiant et mot de passe demandés
    outils/verifier_serveur_geonature.py <url_geonature> --public   # sans connexion : routes publiques seules

À lancer dans un terminal : l'identifiant et le mot de passe se tapent au clavier. Le mot de passe
n'est jamais passé en argument, ni affiché, ni écrit. Rien n'est créé ni modifié sur le serveur :
des lectures, et des POST qui ne font que filtrer ou calculer (jeux de données où l'on peut créer,
Explorer, altitudes). Les grosses listes (TaxRef, HABREF complets) ne sont pas téléchargées : un
échantillon suffit. La liste statique des routes : outils/lister_endpoints_geonature.py.
"""
import getpass
import http.cookiejar
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, timedelta

G = "\033[32m"; Y = "\033[33m"; R = "\033[31m"; B = "\033[36m"; Z = "\033[0m"
OK = f"{G}  OK  {Z}"; ATTENTION = f"{Y}ATTENT{Z}"; ECHEC = f"{R}ÉCHEC {Z}"

# Version minimale (TaxHub intégré à GeoNature) et version de référence du portage.
VERSION_MINIMALE = (2, 15)
VERSION_REFERENCE = "2.17.5"

# Widgets de champs additionnels que l'appli sait afficher (network/AdditionalFields.kt) ; une
# datalist n'y est affichable qu'avec ses valeurs dans le champ, sans API.
WIDGETS_GERES = {
    "text", "textarea", "number", "select", "radio", "checkbox", "bool_checkbox", "bool_radio",
    "nomenclature", "html", "date", "time", "multiselect", "taxonomy", "observers", "observers-text",
}

# Un point et une petite emprise pour les calculs (altitudes, Explorer) : seul le statut compte.
LAT, LON = 46.6, 2.4

compteurs = {"ok": 0, "attention": 0, "echec": 0}


def afficher(statut, libelle, detail=""):
    compteurs[{OK: "ok", ATTENTION: "attention", ECHEC: "echec"}[statut]] += 1
    print(f"  [{statut}] {libelle}" + (f" — {detail}" if detail else ""))


def nb(n, singulier, pluriel=None):
    """« 1 liste », « 3 listes » ; 0 au singulier, comme en français."""
    return f"{n} {singulier if n in (0, 1) else (pluriel or singulier + 's')}"


def version_tuple(texte):
    try:
        return tuple(int(x) for x in str(texte).split(".")[:2])
    except ValueError:
        return None


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    public = "--public" in sys.argv
    if len(args) != 1:
        print(__doc__)
        sys.exit(2)
    base = args[0].rstrip("/")
    api = base + "/api"

    jar = http.cookiejar.CookieJar()
    ouvreur = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    ouvreur.addheaders = [("User-Agent", "geomys-verif"), ("Accept", "application/json")]
    jeton = {"valeur": None}

    def appel(chemin, corps=None, delai=25):
        """GET (ou POST JSON si [corps]) sur `<base>/api<chemin>`, ou sur une adresse complète.
        Rend (code, texte) ; code None si le réseau échoue. Le corps n'est jamais affiché."""
        url = chemin if chemin.startswith("http") else api + chemin
        donnees = json.dumps(corps).encode() if corps is not None else None
        entetes = {"Content-Type": "application/json"} if donnees else {}
        if jeton["valeur"]:
            entetes["Authorization"] = f"Bearer {jeton['valeur']}"
        print(f"    {B}→ {'POST' if donnees else 'GET'} {url}{Z}")
        requete = urllib.request.Request(url, data=donnees, headers=entetes)
        try:
            with ouvreur.open(requete, timeout=delai) as r:
                return r.getcode(), r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            return None, str(e)

    def json_ou_none(texte):
        try:
            return json.loads(texte)
        except Exception:  # noqa: BLE001
            return None  # typiquement une page HTML : route protégée ou absente

    def liste(d):
        if isinstance(d, dict):
            for cle in ("data", "items", "results", "values"):
                if isinstance(d.get(cle), list):
                    return d[cle]
        return d if isinstance(d, list) else []

    print(f"== Vérification GeoNature pour GeoMys : {base} ==")

    # ── 1. Connexion ──
    connecte = False
    id_organisme = None
    if public:
        afficher(ATTENTION, "Connexion", "non demandée (--public) : les routes protégées ressortiront en ATTENTION")
    else:
        identifiant = input("Identifiant GeoNature : ").strip()
        mot_de_passe = getpass.getpass("Mot de passe (non affiché) : ")
        code, texte = appel("/auth/login", {"login": identifiant, "password": mot_de_passe})
        mot_de_passe = None
        # GeoNature répond 200 même à de mauvais identifiants : seul le jeton prouve la connexion.
        d = json_ou_none(texte) if code == 200 else None
        if isinstance(d, dict):
            utilisateur = d.get("user") or {}
            jeton["valeur"] = (d.get("access_token") or d.get("token")
                               or utilisateur.get("access_token") or utilisateur.get("token"))
            id_organisme = utilisateur.get("id_organisme")
        if not jeton["valeur"]:
            jeton["valeur"] = next((c.value for c in jar if c.name == "token" and c.value), None)
        if code == 200 and jeton["valeur"]:
            connecte = True
            afficher(OK, "Connexion", f"jeton reçu, organisme {id_organisme}")
        elif code == 200:
            afficher(ECHEC, "Connexion", "identifiants refusés (HTTP 200 sans jeton)")
        elif code in (301, 302, 303, 307, 308):
            afficher(ECHEC, "Connexion", f"redirection HTTP {code} : adresse du serveur à revoir (…/geonature ?)")
        else:
            afficher(ECHEC, "Connexion", f"HTTP {code}")

    def protegee(libelle, code):
        """Verdict d'une route protégée en échec : ÉCHEC si l'on est connecté, ATTENTION sinon."""
        afficher(ECHEC if connecte else ATTENTION, libelle, f"HTTP {code}" + ("" if connecte else " (connexion requise ?)"))

    # ── 2. Configuration du serveur ──
    occtax = occhab = {}
    id_observateurs = id_liste_taxons = None
    code, texte = appel("/gn_commons/config")
    cfg = json_ou_none(texte) if code == 200 else None
    if isinstance(cfg, dict):
        version = cfg.get("GEONATURE_VERSION") or "?"
        v = version_tuple(version)
        if v is None:
            afficher(ATTENTION, "Version de GeoNature", f"{version} illisible")
        elif v < VERSION_MINIMALE:
            afficher(ECHEC, "Version de GeoNature", f"{version} : 2.15 au moins (TaxHub intégré)")
        else:
            au_dela = v > version_tuple(VERSION_REFERENCE)
            afficher(ATTENTION if au_dela else OK, "Version de GeoNature",
                     f"{version}" + (f" : plus récente que {VERSION_REFERENCE}, la version de référence" if au_dela else ""))
        occtax = cfg.get("OCCTAX") or {}
        occhab = cfg.get("OCCHAB") or {}
        id_observateurs = occtax.get("id_observers_list")
        id_liste_taxons = occtax.get("id_taxon_list")
        afficher(OK if isinstance(occtax.get("form_fields"), dict) else ATTENTION, "Formulaire OccTax (OCCTAX)",
                 f"form_fields {'présent' if isinstance(occtax.get('form_fields'), dict) else 'absent : tous les champs montrés'}, "
                 f"id_observers_list={id_observateurs}, id_taxon_list={id_liste_taxons}, CD_TYPO_HABITAT={occtax.get('CD_TYPO_HABITAT')}")
        if occhab:
            afficher(OK, "Formulaire OccHab (OCCHAB)",
                     f"OBSERVER_LIST_ID={occhab.get('OBSERVER_LIST_ID')}, ID_LIST_HABITAT={occhab.get('ID_LIST_HABITAT')}, "
                     f"formConfig {'présent' if occhab.get('formConfig') else 'absent'}")
        niveaux = (cfg.get("MONITORINGS") or {}).get("PERMISSION_LEVEL")
        afficher(OK if niveaux else ATTENTION, "Droits des Suivis (MONITORINGS.PERMISSION_LEVEL)",
                 "présents" if niveaux else "absents : droits de création par défaut")
    else:
        afficher(ECHEC, "gn_commons/config", f"HTTP {code} : version, formulaires et listes inconnus")

    # ── 3. Réglages mobiles (t_mobile_apps) ──
    taxhub = base + "/api/taxhub"
    code, texte = appel("/gn_commons/t_mobile_apps")
    apps = liste(json_ou_none(texte)) if code == 200 else None
    if apps is None or code != 200:
        afficher(ATTENTION, "Réglages mobiles (t_mobile_apps)",
                 f"HTTP {code} : défauts appliqués (la config du formulaire est lue quand même depuis le 21 octobre)")
    else:
        app = next((a for a in apps if isinstance(a, dict) and str(a.get("app_code", "")).upper() == "OCCTAX"), None)
        reglages = (app or {}).get("settings") or {}
        url_taxhub = ((reglages.get("sync") or {}).get("taxhub_url") or "").strip().rstrip("/")
        if url_taxhub:
            taxhub = url_taxhub
        if app is None:
            afficher(ATTENTION, "Réglages mobiles (t_mobile_apps)", "pas d'appli OCCTAX : défauts appliqués")
        else:
            meme = urllib.parse.urlparse(taxhub).netloc == urllib.parse.urlparse(base).netloc
            afficher(OK, "Réglages mobiles (t_mobile_apps)",
                     f"TaxHub {'sur le même serveur' if meme else 'sur une autre adresse : ' + taxhub}, "
                     f"page_size={(reglages.get('sync') or {}).get('page_size')}, "
                     f"area_observation_duration={reglages.get('area_observation_duration')}")

    # ── 4. Modules et droits ──
    modules = {}
    code, texte = appel("/gn_commons/modules")
    lus = liste(json_ou_none(texte)) if code == 200 else None
    if lus:
        for m in lus:
            if isinstance(m, dict) and m.get("module_code"):
                modules[str(m["module_code"]).upper()] = m.get("cruved") or {}
        droits = lambda code_module: modules.get(code_module, {})  # noqa: E731
        cree_occtax = (droits("OCCTAX").get("C") or 0) > 0
        afficher(OK if "OCCTAX" in modules and cree_occtax else ECHEC, "Module OccTax",
                 "présent, création permise" if cree_occtax else
                 ("présent, sans droit de création : Paramètres ne pourra pas être complété" if "OCCTAX" in modules else "absent"))
        if "OCCHAB" in modules:
            afficher(OK, "Module OccHab", f"lecture {droits('OCCHAB').get('R', 0)}, création {droits('OCCHAB').get('C', 0)}")
        else:
            afficher(ATTENTION, "Module OccHab", "absent ou sans droit : tuile masquée")
    else:
        protegee("gn_commons/modules (droits)", code)

    # ── 5. Observateurs d'OccTax ──
    if id_observateurs:
        code, texte = appel(f"/users/menu/{id_observateurs}")
        obs = liste(json_ou_none(texte)) if code == 200 else None
        if obs:
            champs = isinstance(obs[0], dict) and {"id_role", "nom_complet"} <= set(obs[0])
            afficher(OK if champs else ATTENTION, f"Observateurs (users/menu/{id_observateurs})",
                     nb(len(obs), "personne") + ("" if champs else " ; id_role ou nom_complet absents"))
        elif code == 200:
            afficher(ATTENTION, f"Observateurs (users/menu/{id_observateurs})", "liste vide, comme sur le web")
        else:
            protegee(f"Observateurs (users/menu/{id_observateurs})", code)
    else:
        afficher(ECHEC, "Observateurs", "pas d'OCCTAX.id_observers_list : aucune liste (plus de repli sur users/roles)")

    # ── 6. Jeux de données ──
    for module in ["OCCTAX"] + (["OCCHAB"] if "OCCHAB" in modules else []):
        code, texte = appel(f"/meta/datasets?active=true&module_code={module}&fields=modules", delai=90)
        jeux = liste(json_ou_none(texte)) if code == 200 else None
        if jeux is None:
            protegee(f"Jeux de données {module} (lecture)", code)
            continue
        # Le GET ignore active et module_code (lus seulement dans un corps JSON) : l'appli refiltre.
        retenus = [j for j in jeux if isinstance(j, dict) and j.get("active", True)
                   and any((x.get("module_code") if isinstance(x, dict) else x) == module for x in (j.get("modules") or []))]
        code, texte = appel("/meta/datasets?active=true", {"create": module}, delai=90)
        creables = liste(json_ou_none(texte)) if code == 200 else None
        if creables is None:
            protegee(f"Jeux de données {module} où créer (POST create)", code)
            continue
        ids = {j.get("id_dataset") for j in creables if isinstance(j, dict)}
        proposes = [j for j in retenus if j.get("id_dataset") in ids]
        afficher(OK if proposes else (ECHEC if module == "OCCTAX" else ATTENTION), f"Jeux de données {module}",
                 f"{nb(len(retenus), 'actif')}, {nb(len(proposes), 'proposé')} à la saisie"
                 + ("" if proposes or module != "OCCTAX" else " : Paramètres ne pourra pas être complété"))

    # ── 7. Champs additionnels d'OccTax ──
    code, texte = appel("/gn_commons/additional_fields?module_code=OCCTAX")
    champs = liste(json_ou_none(texte)) if code == 200 else None
    if champs is None:
        afficher(ATTENTION if code == 404 else ECHEC, "Champs additionnels", f"HTTP {code}" + (" : aucun" if code == 404 else ""))
    else:
        def widget(c):
            w = c.get("type_widget")
            nom = (w.get("widget_name") if isinstance(w, dict) else w) or ""
            if nom == "datalist" and not c.get("field_values"):
                return "datalist (api)"
            return nom
        non_geres = [c for c in champs if isinstance(c, dict) and widget(c) not in WIDGETS_GERES]
        requis_non_geres = [c for c in non_geres if c.get("required")]
        afficher(ECHEC if requis_non_geres else (ATTENTION if non_geres else OK), "Champs additionnels d'OccTax",
                 f"{nb(len(champs), 'champ')}, {nb(sum(1 for c in champs if isinstance(c, dict) and c.get('required')), 'requis', 'requis')}"
                 + (f" ; widgets non gérés : {sorted({widget(c) for c in non_geres})}" if non_geres else "")
                 + (" — un requis non géré bloque l'écran" if requis_non_geres else ""))

    # ── 8. Nomenclatures et valeurs par défaut ──
    for chemin in ("/nomenclatures/nomenclatures/taxonomy", "/nomenclatures/nomenclature/taxonomy", "/nomenclatures/taxonomy"):
        code, texte = appel(chemin, delai=60)
        types = liste(json_ou_none(texte)) if code == 200 else None
        if types:
            afficher(OK if chemin.startswith("/nomenclatures/nomenclatures/") else ATTENTION, "Nomenclatures",
                     nb(len(types), "type") + ("" if chemin.startswith("/nomenclatures/nomenclatures/") else f" (repli {chemin})"))
            break
    else:
        protegee("Nomenclatures", code)
    code, texte = appel("/occtax/defaultNomenclatures" + (f"?organism={id_organisme}" if id_organisme else ""))
    defauts = json_ou_none(texte) if code == 200 else None
    if isinstance(defauts, dict):
        afficher(OK, "Valeurs par défaut d'OccTax", nb(len(defauts), "type"))
    else:
        protegee("Valeurs par défaut d'OccTax", code)
    if "OCCHAB" in modules:
        code, texte = appel("/occhab/defaultNomenclatures")
        if code == 200 and isinstance(json_ou_none(texte), dict):
            afficher(OK, "Valeurs par défaut d'OccHab")
        else:
            protegee("Valeurs par défaut d'OccHab", code)

    # ── 9. TaxHub (public) ──
    code, texte = appel(taxhub + "/api/taxref/version")
    d = json_ou_none(texte) if code == 200 else None
    if isinstance(d, dict):
        afficher(OK, "Version de TaxRef", str(d.get("version") or d.get("taxref_version") or "?"))
    else:
        afficher(ATTENTION, "Version de TaxRef", f"HTTP {code} : TaxRef sera retéléchargé à chaque chargement")
    premiere_liste = None
    code, texte = appel(taxhub + "/api/biblistes")
    listes = liste(json_ou_none(texte)) if code == 200 else None
    if listes:
        premiere_liste = listes[0].get("id_liste") if isinstance(listes[0], dict) else None
        afficher(OK, "Listes de taxons (biblistes)", nb(len(listes), "liste"))
    else:
        afficher(ECHEC, "Listes de taxons (biblistes)", f"HTTP {code} : TaxHub mal monté ou sous un autre préfixe que /api ?")
    lid = id_liste_taxons or premiere_liste
    if lid:
        code, texte = appel(taxhub + f"/api/taxref?orderby=cd_nom&fields=listes&id_liste={lid}&limit=2&page=1", delai=60)
        d = json_ou_none(texte) if code == 200 else None
        items = liste(d) if d is not None else None
        if items is not None:
            premier = items[0] if items else {}
            champs_ok = isinstance(premier, dict) and "cd_nom" in premier and "lb_nom" in premier
            total = d.get("total_filtered") if isinstance(d, dict) else None
            afficher(OK if champs_ok else ATTENTION, f"Taxons de la liste {lid}",
                     (nb(total, "taxon") if isinstance(total, int) else "? taxons") + ("" if champs_ok else " ; cd_nom ou lb_nom absents"))
        else:
            afficher(ECHEC, f"Taxons de la liste {lid}", f"HTTP {code}")

    # ── 10. HABREF (public) ──
    filtre = f"&id_list={occtax.get('ID_LIST_HABITAT')}" if occtax.get("ID_LIST_HABITAT") else ""
    code, texte = appel(f"/habref/habitats/autocomplete?search_name=prairie&limit=3{filtre}")
    habitats = json_ou_none(texte) if code == 200 else None
    if isinstance(habitats, list):
        champs_ok = not habitats or ("cd_hab" in habitats[0] and "search_name" in habitats[0])
        afficher(OK if champs_ok else ATTENTION, "Habitats HABREF", f"{nb(len(habitats), 'résultat')} pour « prairie »")
    else:
        afficher(ATTENTION, "Habitats HABREF", f"HTTP {code} : champ habitat indisponible (non bloquant)")

    # ── 11. Suivis ──
    code, texte = appel("/monitorings/modules")
    protocoles = liste(json_ou_none(texte)) if code == 200 else None
    if code in (403, 404):
        afficher(ATTENTION, "Suivis (monitorings/modules)", "module absent" if code == 404 else "aucun droit : tuile masquée")
    elif protocoles is None:
        protegee("Suivis (monitorings/modules)", code)
    else:
        lisibles = [p for p in protocoles if isinstance(p, dict) and ((p.get("cruved") or {}).get("R") or 0) > 0]
        afficher(OK, "Suivis (monitorings/modules)", f"{nb(len(protocoles), 'protocole')}, {nb(len(lisibles), 'lisible')}")
        if lisibles:
            module = lisibles[0].get("module_code")
            code, texte = appel(f"/monitorings/config/{urllib.parse.quote(str(module))}")
            schema = json_ou_none(texte) if code == 200 else None
            afficher(OK if isinstance(schema, dict) and "module" in schema else ECHEC, f"Schéma du protocole {module}",
                     "" if isinstance(schema, dict) else f"HTTP {code}")
            code, texte = appel(f"/monitorings/object/{urllib.parse.quote(str(module))}/module?depth=1")
            objet = json_ou_none(texte) if code == 200 else None
            afficher(OK if isinstance(objet, dict) else ECHEC, f"Objets du protocole {module}",
                     nb(sum(len(v) for v in (objet.get('children') or {}).values()), "enfant") if isinstance(objet, dict) else f"HTTP {code}")

    # ── 12. OccHab : la route des stations, sans rien télécharger (jeu -1) ──
    if "OCCHAB" in modules:
        code, texte = appel("/occhab/stations/?format=geojson&habitats=1&nomenclatures=1&id_dataset=-1")
        afficher(OK if code == 200 and isinstance(json_ou_none(texte), dict) else ECHEC, "Stations OccHab",
                 "" if code == 200 else f"HTTP {code}")

    # ── 13. Envoi : les lectures qui le précèdent (rien n'est créé) ──
    code, texte = appel(f"/occtax/OCCTAX/releves?unique_id_sinp_grp={uuid.uuid4()}&limit=1")
    if code == 200 and json_ou_none(texte) is not None:
        afficher(OK, "Anti-doublon des relevés")
    else:
        protegee("Anti-doublon des relevés (occtax/OCCTAX/releves)", code)
    code, texte = appel("/gn_commons/get_id_table_location/pr_occtax.cor_counting_occtax")
    if code == 200 and str(json_ou_none(texte)).strip().isdigit():
        afficher(OK, "Table des médias d'OccTax")
    else:
        protegee("Table des médias d'OccTax (get_id_table_location)", code)
    point = {"type": "Feature", "properties": {}, "geometry": {"type": "Point", "coordinates": [LON, LAT]}}
    for chemin, libelle in (("/geo/altitude", "Altitudes d'un relevé (geo/altitude)"), ("/geo/info", "Altitudes d'une station (geo/info)")):
        code, texte = appel(chemin, point)
        if code == 200 and json_ou_none(texte) is not None:
            afficher(OK, libelle)
        else:
            afficher(ATTENTION, libelle, f"HTTP {code} : altitudes laissées vides")

    # ── 14. Explorer ──
    emprise = {"type": "Feature", "properties": {}, "geometry": {"type": "Polygon", "coordinates": [[
        [LON - 0.01, LAT - 0.01], [LON + 0.01, LAT - 0.01], [LON + 0.01, LAT + 0.01], [LON - 0.01, LAT + 0.01], [LON - 0.01, LAT - 0.01]]]}}
    code, texte = appel("/synthese/for_web?limit=1",
                        {"geoIntersection": emprise, "date_min": (date.today() - timedelta(days=365)).isoformat()})
    if code == 200 and json_ou_none(texte) is not None:
        afficher(OK, "Explorer (synthese/for_web)")
    else:
        afficher(ATTENTION, "Explorer (synthese/for_web)", f"HTTP {code} : Explorer vide (droit de lecture sur la Synthèse ?)")

    print(f"\nLégende : [{OK}] conforme · [{ATTENTION}] repli ou dégradation, non bloquant · [{ECHEC}] à examiner")
    print(f"Bilan : {compteurs['ok']} OK, {compteurs['attention']} ATTENTION, {compteurs['echec']} ÉCHEC")
    sys.exit(1 if compteurs["echec"] else 0)


if __name__ == "__main__":
    main()
