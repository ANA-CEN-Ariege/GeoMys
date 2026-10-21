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

// Propre au portage (21 octobre 2026) : l'essai de bout en bout sur un autre serveur que celui de l'ANA,
// demandé par l'utilisateur sur le serveur de démonstration de GeoNature.

package fr.ariegenature.geomys

import com.russhwolf.settings.MapSettings
import fr.ariegenature.geomys.explorer.Explorer
import fr.ariegenature.geomys.location.SourcePositionsSimulee
import fr.ariegenature.geomys.location.SuiviGpsDirect
import fr.ariegenature.geomys.model.GeoNatureDataset
import fr.ariegenature.geomys.model.GeoNatureListe
import fr.ariegenature.geomys.monitoring.AffichageSuivis
import fr.ariegenature.geomys.monitoring.NouvelleVisite
import fr.ariegenature.geomys.monitoring.PreparationFormulaire
import fr.ariegenature.geomys.monitoring.form.FormulaireEtat
import fr.ariegenature.geomys.monitoring.sourcesFormulaire
import fr.ariegenature.geomys.network.MonitoringSync
import fr.ariegenature.geomys.network.http.ClientsGeoNature
import fr.ariegenature.geomys.network.http.moteurHttpPlateforme
import fr.ariegenature.geomys.outils.resumeDonnees
import fr.ariegenature.geomys.outils.synchroniserEnJournalisant
import fr.ariegenature.geomys.plateforme.ExecutionLongue
import fr.ariegenature.geomys.store.CoffreEnMemoire
import fr.ariegenature.geomys.store.FabriqueReglages
import fr.ariegenature.geomys.store.ReglesSelectionOcctax
import fr.ariegenature.geomys.store.configurationComplete
import fr.ariegenature.geomys.store.listeDeSaisieChargee
import fr.ariegenature.geomys.store.io.RacinesStockage
import fr.ariegenature.geomys.store.io.listeDepuisJson
import fr.ariegenature.geomys.util.uuidAleatoire
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.SYSTEM

/** Essai de bout en bout sur un vrai serveur GeoNature, avec les vrais services de l'appli et leur
 *  client HTTP (OkHttp) : connexion, « Recharger les données » complet, sélections de Paramètres,
 *  préparation et contrôle du formulaire de chaque type de saisie de chaque protocole, listes et
 *  fiches des Suivis, Explorer. Rien n'est envoyé au serveur.
 *
 *  Ignoré sans `GEOMYS_ESSAI_URL`, `GEOMYS_ESSAI_IDENTIFIANT` et `GEOMYS_ESSAI_MOT_DE_PASSE` ; le
 *  rapport va dans `GEOMYS_ESSAI_RAPPORT` (à défaut, la sortie du test). À réserver à un compte
 *  d'essai, comme celui, public, du serveur de démonstration : les identifiants passent par
 *  l'environnement. */
class EssaiServeurReelTest {

    @Test
    fun synchro_et_formulaires_sur_un_serveur_reel() {
        val url = System.getenv("GEOMYS_ESSAI_URL") ?: return
        val identifiant = System.getenv("GEOMYS_ESSAI_IDENTIFIANT") ?: return
        val motDePasse = System.getenv("GEOMYS_ESSAI_MOT_DE_PASSE") ?: return
        val rapport = StringBuilder()
        fun note(ligne: String) {
            rapport.appendLine(ligne)
            println(ligne)
        }
        val systeme = FileSystem.SYSTEM
        val dossier = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "geomys-essai-${uuidAleatoire()}"
        val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reglages = mutableMapOf<String, MapSettings>()
        val socle = Socle(
            racines = RacinesStockage(dossier / "fichiers", dossier / "cache"),
            reglages = FabriqueReglages { nom -> reglages.getOrPut(nom) { MapSettings() } },
            coffre = CoffreEnMemoire(),
            clients = ClientsGeoNature(moteurHttpPlateforme(false), moteurHttpPlateforme(true)),
            executionLongue = object : ExecutionLongue {
                override fun lancer(nom: String, avancement: Flow<String>, travail: suspend () -> Unit) {
                    portee.launch { travail() }
                }

                override suspend fun <T> pendant(nom: String, travail: suspend () -> T): T = travail()
            },
            positions = SourcePositionsSimulee(),
            suiviGps = SuiviGpsDirect,
        )
        val services = ServicesGeoNature(socle)
        val config = services.config
        config.urlServeur = url
        config.login = identifiant
        config.motDePasse = motDePasse
        try {
            runBlocking {
                note("== Connexion")
                note(services.verificationConnexion.verifier(config).enLigne().replace(Regex("login=\"[^\"]*\""), "login=…"))

                note("== Recharger les données")
                services.synchroniserEnJournalisant(::note)

                note("== Paramètres : le premier jeu proposé, sa liste ou la première, l'utilisateur connecté")
                val jeux = ReglesSelectionOcctax.datasetsProposes(
                    listeDepuisJson(GeoNatureDataset.serializer(), config.datasetsCacheJson), config.datasetsCreablesOcctax,
                )
                val listes = listeDepuisJson(GeoNatureListe.serializer(), config.listesCacheJson)
                note("jeux_proposes=${jeux.size} listes=${listes.size} utilisateur=${config.idRoleUtilisateur}")
                jeux.firstOrNull()?.let { jeu ->
                    config.idDataset = jeu.id.toString()
                    config.taxaListeId = (jeu.idTaxaList ?: listes.firstOrNull()?.id)?.toString().orEmpty()
                }
                note("jeu=${config.idDataset} liste=${config.taxaListeId} configuration_complete=${configurationComplete(config, services.taxRef)}")
                // Une liste choisie absente du téléphone : Paramètres relance le chargement.
                if (!listeDeSaisieChargee(config, services.taxRef)) {
                    note("== Chargement de la liste choisie, comme Paramètres")
                    services.synchroniserEnJournalisant(::note)
                    note("configuration_complete=${configurationComplete(config, services.taxRef)}")
                }
                note(services.resumeDonnees())

                note("== Suivis : listes, fiches et formulaires de chaque protocole")
                val protocoles = services.monitoringModules.chargerModulesDepuisCache().orEmpty()
                for (protocole in protocoles) {
                    val code = protocole.moduleCode
                    try {
                        val schema = services.monitoring.lireSchemaProtocole(code)
                        if (schema == null) {
                            note("$code : SCHEMA ABSENT DU CACHE")
                            continue
                        }
                        val resolveur = services.monitoringDatalists.lireResolveurLabels(config, schema, code)
                        val enfants = services.monitoring.lireEnfants(code).orEmpty()
                        val tries = AffichageSuivis.enfantsDuProtocole(enfants, schema, resolveur)
                        val types = AffichageSuivis.typesAfficherEnListe(tries.keys.toList(), schema)
                        val lignes = types.sumOf { t -> tries[t].orEmpty().onEach { AffichageSuivis.sousTitrePourEnfant(it, schema[t], resolveur) }.size }
                        // La fiche du premier objet de la première liste.
                        val premier = types.firstOrNull()?.let { t -> tries[t]?.firstOrNull()?.let { t to it } }
                        val fiche = premier?.let { (t, e) -> services.monitoring.lireObjet(code, t, e.id) }
                        val proprietes = fiche?.let { AffichageSuivis.proprietesAffichees(it, schema[it.type], resolveur).size }
                        note("$code : listes=$types lignes=$lignes fiche=${premier?.first}#${premier?.second?.id} proprietes=$proprietes")
                        for (type in schema.keys.filter { MonitoringSync.estTypeSaisie(it) }) {
                            val parent = schema[type]?.parentType
                            when (val p = NouvelleVisite.preparer(services.sourcesFormulaire(), code, type, parent, null, emptyList())) {
                                is PreparationFormulaire.Impossible -> note("  $type : IMPOSSIBLE ${p.messages}")
                                is PreparationFormulaire.Prete -> {
                                    val f = p.formulaire
                                    val etat = FormulaireEtat(f.champs, services.taxRef, portee, f.champsDirtyInitiaux)
                                    etat.setReglesChange(f.schemaObjet.changeRules)
                                    etat.flushChangementsEnAttente()
                                    val manquants = etat.champsObligatoiresManquants()
                                    val invalides = etat.champsInvalides()
                                    etat.lireValeursPourEnvoi()
                                    note(
                                        "  $type : champs=${f.champs.size} visibles=${f.champs.count { etat.estVisible(it.code) }} " +
                                            "obligatoires_vides=${manquants.size} invalides=${invalides.size}" +
                                            (if (f.avertissements.isNotEmpty()) " AVERTISSEMENTS=${f.avertissements}" else "") +
                                            (if (f.proprietesMasqueesManquantes.isNotEmpty()) " MASQUEES_REQUISES_VIDES=${f.proprietesMasqueesManquantes}" else ""),
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        note("$code : ERREUR ${e::class.simpleName} ${e.message}")
                    }
                }

                note("== Explorer (France entière, limite 1 000)")
                try {
                    val obs = services.browse.recupererObsExplorer(config, -5.5, 41.0, 9.8, 51.5)
                    note("observations=${obs.size} titres_inconnus=${obs.count { Explorer.titre(it) == "Espèce inconnue" }} sans_groupe=${obs.count { it.taxon == null }}")
                } catch (e: Exception) {
                    note("Explorer : ERREUR ${e::class.simpleName} ${e.message}")
                }
            }
        } finally {
            System.getenv("GEOMYS_ESSAI_RAPPORT")?.let { File(it).writeText(rapport.toString()) }
            portee.cancel()
            systeme.deleteRecursively(dossier)
        }
    }
}
