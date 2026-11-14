/*
 * GeoMys — application de saisie naturaliste pour GeoNature, Android et iOS.
 * Copyright (C) 2026 ANA - CEN Ariège
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// Propre au portage : les serveurs proposés par le champ « Serveur GeoNature » des Paramètres
// (demande du 13 novembre 2026). Liste reprise de BiodiVisio (OtusEco, GPLv3,
// lib/login/login_screen.dart) : des instances où l'on peut se créer un compte, ou en accès public ;
// celles de l'ANA - CEN Ariège (production et tests) ajoutées à la demande de l'utilisateur.

package fr.ariegenature.geomys.network

import fr.ariegenature.geomys.store.ListeTaxons

/** Un serveur GeoNature proposé : son [nom] et son [url], celle du site web, sans `/api`. */
data class ServeurGeoNature(val nom: String, val url: String)

object ServeursGeoNature {

    /** Dans l'ordre de BiodiVisio : alphabétique, la démonstration à la fin. */
    val proposes: List<ServeurGeoNature> = listOf(
        ServeurGeoNature("ANA - CEN Ariège", "https://geonature.ariegenature.fr/geonature"),
        ServeurGeoNature("ANA - CEN Ariège - Tests", "https://geonaturetest.intranet.ariegenature.fr/geonature"),
        ServeurGeoNature("Biodiv'Aura Expert", "https://donnees.biodiversite-auvergne-rhone-alpes.fr"),
        ServeurGeoNature("Biodiv'Bretagne", "https://data.biodiversite-bretagne.fr/geonature"),
        ServeurGeoNature("Biodiv'Occitanie", "https://geonature.biodiv-occitanie.fr"),
        ServeurGeoNature("Clicnat", "https://clicnat.fr/geonature"),
        ServeurGeoNature("GeoNat'îdF", "https://geonature.arb-idf.fr/geonature"),
        ServeurGeoNature("Ginco Pays de la Loire", "https://ginco-paysdelaloire.mnhn.fr/geonature"),
        ServeurGeoNature("Helix (CEN PACA)", "https://helix.cen-paca.org"),
        ServeurGeoNature("La SHF", "https://geonature.lashf.org"),
        ServeurGeoNature("Lo Parvi", "https://geonature.loparvi.fr"),
        ServeurGeoNature("Parc national des Écrins", "https://geonature.ecrins-parcnational.fr"),
        ServeurGeoNature("Parc national des Cévennes", "https://appli.cevennes-parcnational.net/geonature"),
        ServeurGeoNature("Parc national de forêts", "https://geonature.forets-parcnational.fr/geonature"),
        ServeurGeoNature("Parc amazonien de Guyane", "https://geonature.parc-amazonien-guyane.fr/geonature"),
        ServeurGeoNature("Parc national des Pyrénées", "https://geonature.pyrenees-parcnational.fr/geonature"),
        ServeurGeoNature("PNR du Marais poitevin", "https://geonature.parc-marais-poitevin.fr"),
        ServeurGeoNature("PNR Normandie-Maine", "https://geonature.parc-naturel-normandie-maine.fr/geonature"),
        ServeurGeoNature("Réensauvager la Ferme", "https://reensauvagerlaferme.fr/geonature"),
        ServeurGeoNature("SIFlora Expert (CBN Alpin)", "https://geonature.cbn-alpin.fr"),
        ServeurGeoNature("Silene", "https://expert.silene.eu"),
        ServeurGeoNature("Démo GeoNature", "https://demo.geonature.fr/geonature"),
    )

    /** Les serveurs dont le nom ou l'adresse contient [saisie], ni la casse ni les accents ne
     *  comptant ; saisie vide : tous. */
    fun filtrer(saisie: String): List<ServeurGeoNature> {
        val cle = ListeTaxons.normaliser(saisie.trim())
        if (cle.isEmpty()) return proposes
        return proposes.filter { ListeTaxons.normaliser(it.nom).contains(cle) || ListeTaxons.normaliser(it.url).contains(cle) }
    }
}
