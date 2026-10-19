/*
 * GeoMys-Common — application de saisie naturaliste pour GeoNature, Android et iOS.
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

// Porté de GeoMys-Android @ cdd5196 (app/src/test/java/fr/ariegenature/geomys/FormulaireRendererTest.kt) :
// réécrit contre l'état commun du formulaire. Les saisies dans les vues deviennent les saisies de
// l'état (saisirTexte…), le Looper de Robolectric le planificateur de runTest, la barre rouge du
// mode complétion la liste barresRouges. Le cas « la barre est accolée au champ, pas au libellé »,
// qui vérifie la mise en page, suivra avec l'écran (lot 7).

package fr.ariegenature.geomys.monitoring.form

import fr.ariegenature.geomys.ServicesDeTest
import fr.ariegenature.geomys.store.TaxRefCache
import fr.ariegenature.geomys.store.TaxRefEntry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * ASSEMBLAGE du moteur de formulaire des Suivis ([FormulaireEtat]) — trou de couverture de l'audit
 * 2026-08-27 : rendu → lecture typée des valeurs (texte, décimal avec virgule, entier strict), champs
 * obligatoires manquants (mis à jour par la saisie), règle `change` appliquée par le flush
 * synchrone, sans boucle ni écrasement d'un champ modifié par l'utilisateur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FormulaireEtatTest {

    private val t = ServicesDeTest()
    private val taxRef get() = t.services.taxRef

    @AfterTest
    fun nettoyer() = t.nettoyer()

    private fun TestScope.formulaire(champs: List<EditableField>) = FormulaireEtat(champs, taxRef, backgroundScope)

    @Test
    fun lecture_typee_des_valeurs_rendues() = runTest {
        val etat = formulaire(listOf(
            EditableField("nom", ViewType.TEXT, "Nom", value = "Merle"),
            EditableField("taux", ViewType.NUMBER, "Taux", value = "12,5", decimal = true),
            EditableField("nb", ViewType.NUMBER, "Nombre", value = "7"),
            EditableField("vide", ViewType.NUMBER, "Vide"),
        ))
        val v = etat.lireValeurs()
        assertEquals("Merle", v["nom"])
        assertEquals(12.5, v["taux"], "virgule décimale acceptée")
        assertEquals(7, v["nb"], "entier reste entier")
        assertNull(v["vide"])
    }

    @Test
    fun widget_entier_ne_renvoie_jamais_de_decimal() = runTest {
        val etat = formulaire(listOf(EditableField("nb", ViewType.NUMBER, "Nombre", value = "3")))
        etat.saisirTexte("nb", "4.7") // collé / posé par une règle
        etat.flushChangementsEnAttente()
        assertEquals(4, etat.lireValeurs()["nb"], "tronqué en entier (audit lot C)")
    }

    @Test
    fun champs_obligatoires_manquants_suivent_la_saisie() = runTest {
        val etat = formulaire(listOf(
            EditableField("nom", ViewType.TEXT, "Nom", obligatoire = true),
            EditableField("nb", ViewType.NUMBER, "Nombre", obligatoire = true, value = "1"),
        ))
        val manquants = etat.champsObligatoiresManquants()
        assertEquals(1, manquants.size)
        assertTrue(manquants.single().contains("Nom", ignoreCase = true) || manquants.single() == "nom",
            "le champ vide est signalé : $manquants")
        etat.saisirTexte("nom", "Grive")
        etat.flushChangementsEnAttente()
        assertTrue(etat.champsObligatoiresManquants().isEmpty())
    }

    @Test
    fun regle_change_appliquee_au_flush_sans_ecraser_un_champ_modifie_par_l_utilisateur() = runTest {
        val etat = formulaire(listOf(
            EditableField("presence", ViewType.TEXT, "Présence", value = "Oui"),
            EditableField("count_min", ViewType.NUMBER, "Min", value = "5"),
            EditableField("count_max", ViewType.NUMBER, "Max", value = "9"),
        ))
        // Garde `!dirty` des scripts gn_module_monitoring : le patch ne s'applique que tant que
        // l'utilisateur n'a pas touché count_min lui-même.
        etat.setReglesChange(listOf(
            "({objForm, meta}) => {",
            "if (objForm.value.presence === 'Non' && !objForm.controls.count_min.dirty) {",
            "objForm.patchValue({count_min: 0, count_max: 0})",
            "}",
            "}",
        ))
        etat.saisirTexte("presence", "Non")
        etat.flushChangementsEnAttente()
        runCurrent()
        val v = etat.lireValeurs()
        assertEquals(0, v["count_min"], "patch appliqué (règle change)")
        assertEquals(0, v["count_max"])
        // L'utilisateur reprend la main sur count_min → dirty : un nouveau déclenchement de la règle
        // ne doit pas réécraser sa valeur (garde !dirty), et ne doit pas boucler.
        etat.saisirTexte("count_min", "3")
        etat.flushChangementsEnAttente()
        etat.saisirTexte("presence", "Non ")
        etat.flushChangementsEnAttente()
        runCurrent()
        assertEquals(3, etat.lireValeurs()["count_min"], "champ modifié par l'utilisateur conservé")
    }

    // ── Mode complétion : barre verticale rouge sur les obligatoires VIDES ─────────────
    // Demande terrain 2026-09-03 : quand on ouvre un formulaire pour finir les champs manquants, ils
    // doivent se repérer d'un coup d'œil.

    @Test
    fun barre_rouge_seulement_sur_les_obligatoires_vides() = runTest {
        val etat = formulaire(listOf(
            EditableField("rempli", ViewType.TEXT, "Rempli", value = "x", obligatoire = true),
            EditableField("vide", ViewType.TEXT, "Vide", obligatoire = true),
            EditableField("libre", ViewType.TEXT, "Facultatif"),
        ))
        val barres = etat.barresRouges(modeCompletion = true)
        assertTrue("vide" in barres, "obligatoire VIDE → barre")
        assertFalse("rempli" in barres, "obligatoire déjà rempli → pas de barre")
        assertFalse("libre" in barres, "champ facultatif → pas de barre")
    }

    @Test
    fun la_barre_disparait_quand_le_champ_est_rempli() = runTest {
        val etat = formulaire(listOf(EditableField("vide", ViewType.TEXT, "Vide", obligatoire = true)))
        assertTrue("vide" in etat.barresRouges(modeCompletion = true))
        etat.saisirTexte("vide", "enfin saisi")
        etat.flushChangementsEnAttente()
        assertFalse("vide" in etat.barresRouges(modeCompletion = true), "champ rempli → barre retirée")
    }

    @Test
    fun hors_mode_completion_aucune_barre() = runTest {
        val etat = formulaire(listOf(EditableField("vide", ViewType.TEXT, "Vide", obligatoire = true)))
        assertTrue(etat.barresRouges(modeCompletion = false).isEmpty())
    }

    // ── Champs masqués et envoi (audit 2026-09-14, R4-M4 / R4-M3) ──

    @Test
    fun un_champ_masque_conserve_sa_valeur_mais_ne_part_PAS_au_serveur() = runTest {
        // Un protocole qui masque « nombre de nids » quand « espèce observée = non » transmettait
        // quand même le nombre saisi avant la bascule : donnée fausse en base, sans aucun signal. Le
        // client mobile officiel PnX-SI, lui, n'envoie pas les champs masqués.
        val etat = formulaire(listOf(
            EditableField("vue", ViewType.TEXT, "Espèce observée", value = "non"),
            EditableField("nids", ViewType.NUMBER, "Nombre de nids", value = "3", hiddenExpr = "\${vue} === 'non'"),
        ))

        assertEquals(3, etat.lireValeurs()["nids"],
            "la valeur reste lisible : masquer n'est pas effacer, l'utilisateur la retrouve si la condition redevient vraie")
        assertTrue(!etat.lireValeursPourEnvoi().containsKey("nids"), "mais elle ne doit pas partir au serveur")
        assertEquals("non", etat.lireValeursPourEnvoi()["vue"], "les champs visibles partent normalement")
    }

    @Test
    fun un_champ_masque_mais_REQUIS_part_quand_meme() = runTest {
        // Exception voulue, alignée sur le client officiel : le serveur refuserait la saisie
        // autrement, et une incohérence du schéma ne doit pas faire perdre une visite de terrain.
        val etat = formulaire(listOf(
            EditableField("vue", ViewType.TEXT, "Espèce observée", value = "non"),
            EditableField("nids", ViewType.NUMBER, "Nombre de nids", value = "3",
                obligatoire = true, hiddenExpr = "\${vue} === 'non'"),
        ))
        assertEquals(3, etat.lireValeursPourEnvoi()["nids"])
    }

    @Test
    fun setReglesChange_recalcule_l_etat_du_formulaire() = runTest {
        // appliquerChangeRules ne fait que POSER des valeurs. Sans recalcul derrière, la visibilité
        // conditionnelle et l'état du bouton restaient ceux d'AVANT la règle.
        val etat = formulaire(listOf(
            EditableField("mode", ViewType.TEXT, "Mode", value = "auto"),
            EditableField("calcule", ViewType.TEXT, "Calculé"),
        ))
        var notifie = false
        etat.setOnChangement { notifie = true }
        notifie = false

        etat.setReglesChange(listOf("calcule = 'rempli par la règle'"))

        assertTrue(notifie, "le formulaire doit être notifié du recalcul")
    }

    /**
     * Le champ espèce d'un protocole à liste taxonomique ne doit plus poser le cd_nom d'un taxon HORS
     * liste (audit 2026-09-17, C1). Cas réel : sur STERF (liste 109), « Souci » est *Colias crocea*
     * (641941) — la clé du cache appartient pourtant à *Calendula* (190178), la plante, qui partait à
     * GeoNature comme observation de papillon. Testé ICI, sur l'état du formulaire, et pas seulement
     * sur TaxRefCache : c'est lui qui décide ce que `lireValeurs` renverra à l'envoi.
     */
    @Test
    fun champ_taxon_d_un_protocole_ne_resout_pas_hors_de_sa_liste() = runTest {
        taxRef.vider()
        taxRef.remplacerTout(mapOf(
            TaxRefCache.normaliser("Souci") to TaxRefEntry(190178, "Calendula", "Souci"),
            TaxRefCache.normaliser("Colias crocea") to TaxRefEntry(641941, "Colias crocea", null),
        ))
        taxRef.ajouterVerns(mapOf(190178 to listOf("Souci"), 641941 to listOf("Souci")))
        taxRef.ajouterListesParCdNom(mapOf(641941 to listOf(100, 109), 190178 to listOf(100)))

        val etat = formulaire(listOf(
            EditableField("esp", ViewType.TAXON, "Espèce", idListeTaxonomieRestreinte = 109),
            EditableField("esp_libre", ViewType.TAXON, "Espèce (sans liste)"),
        ))
        etat.saisirTexteTaxon("esp", "Souci")
        etat.saisirTexteTaxon("esp_libre", "Souci")

        val v = etat.lireValeurs()
        assertEquals(641941, v["esp"], "le protocole impose sa liste : c'est le papillon")
        assertEquals(190178, v["esp_libre"], "sans liste, la résolution globale est inchangée")
    }

    /** Un nom étranger au protocole n'est pas remplacé en silence par un taxon d'ailleurs : aucune
     *  valeur n'est posée (le champ signale « non reconnue » à sa sortie). */
    @Test
    fun champ_taxon_refuse_un_nom_etranger_au_protocole() = runTest {
        taxRef.vider()
        taxRef.remplacerTout(mapOf(
            TaxRefCache.normaliser("Genette") to TaxRefEntry(60831, "Genetta genetta", "Genette"),
            TaxRefCache.normaliser("Colias crocea") to TaxRefEntry(641941, "Colias crocea", null),
        ))
        taxRef.ajouterVerns(mapOf(60831 to listOf("Genette"), 641941 to listOf("Souci")))
        taxRef.ajouterListesParCdNom(mapOf(641941 to listOf(100, 109), 60831 to listOf(100)))

        val etat = formulaire(listOf(EditableField("esp", ViewType.TAXON, "Espèce", idListeTaxonomieRestreinte = 109)))
        etat.saisirTexteTaxon("esp", "Genette")
        assertNull(etat.lireValeurs()["esp"])
    }

    /** Un texte d'espèce non reconnu n'est plus ignoré en silence : il rend le formulaire INVALIDE,
     *  ce qui grise le bouton d'enregistrement. Règle produit 2026-09-17. */
    @Test
    fun un_nom_d_espece_non_reconnu_rend_le_formulaire_invalide() = runTest {
        taxRef.vider()
        taxRef.remplacerTout(mapOf(
            TaxRefCache.normaliser("Colias crocea") to TaxRefEntry(641941, "Colias crocea", null),
        ))
        taxRef.ajouterVerns(mapOf(641941 to listOf("Souci")))
        taxRef.ajouterListesParCdNom(mapOf(641941 to listOf(100, 109)))

        val etat = formulaire(listOf(EditableField("esp", ViewType.TAXON, "Espèce", idListeTaxonomieRestreinte = 109)))

        etat.saisirTexteTaxon("esp", "Bidule inexistant")
        assertTrue(etat.champsInvalides().contains("esp"), "un nom inconnu doit bloquer")

        etat.saisirTexteTaxon("esp", "Souci")
        assertFalse(etat.champsInvalides().contains("esp"), "un nom du protocole ne bloque pas")

        etat.saisirTexteTaxon("esp", "")
        assertFalse(etat.champsInvalides().contains("esp"), "un champ vide ne bloque pas (c'est `obligatoire` qui s'en charge)")
    }

    /** Liste du protocole absente du cache : aucune proposition, et le moindre texte saisi rend le
     *  formulaire invalide (le champ affiche « Pas de données — rechargez les données »). Avant,
     *  l'écran se repliait sur TOUTES les espèces du cache et acceptait n'importe quel taxon, hors
     *  protocole compris. */
    @Test
    fun liste_du_protocole_absente_du_cache_rien_n_est_propose_ni_accepte() = runTest {
        taxRef.vider()
        taxRef.remplacerTout(mapOf(
            TaxRefCache.normaliser("Colias crocea") to TaxRefEntry(641941, "Colias crocea", null),
            // La clé « souci » appartient à la plante : sans garde, le repli global la poserait.
            TaxRefCache.normaliser("Souci") to TaxRefEntry(190178, "Calendula", "Souci"),
        ))
        taxRef.ajouterVerns(mapOf(641941 to listOf("Souci"), 190178 to listOf("Souci")))
        // Les taxons sont en cache, mais AUCUNE appartenance à la liste 109 n'a été synchronisée.
        taxRef.ajouterListesParCdNom(mapOf(641941 to listOf(100), 190178 to listOf(100)))

        val etat = formulaire(listOf(EditableField("esp", ViewType.TAXON, "Espèce", idListeTaxonomieRestreinte = 109)))
        etat.saisirTexteTaxon("esp", "Souci")
        assertNull(etat.lireValeurs()["esp"], "aucun cd_nom ne doit être posé")
        assertTrue(etat.champsInvalides().contains("esp"), "et le formulaire est invalide")
    }
}
