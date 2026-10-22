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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/store/MbtilesStore.kt), et les
// textes du dialogue des cartes (ui/CacheManagerFragment.kt). Le fichier choisi arrive en flux, que la
// plateforme ouvre (sélecteur de documents) ; SQLite est lu par [LecteurMbtiles].

package fr.ariegenature.geomys.carte

import co.touchlab.kermit.Logger
import fr.ariegenature.geomys.plateforme.exclureDesSauvegardes
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.FileSystem
import okio.Path
import okio.Source
import okio.use

/** Métadonnées utiles d'une carte MBTiles. [format] : « png » ou « jpg ». [nom] : le nom déclaré dans
 *  le fichier (`metadata.name`), s'il y en a un. */
data class InfoMbtiles(val zoomMin: Int, val zoomMax: Int, val format: String, val nom: String?)

/** Ce que l'appli lit d'un fichier MBTiles, une base SQLite. */
interface LecteurMbtiles {
    /** La table `metadata` (nom → valeur), null si elle est illisible. */
    fun metadonnees(fichier: Path): Map<String, String>?

    /** Les zooms extrêmes de la table `tiles`, null si elle est vide ou illisible. */
    fun zoomsDesTuiles(fichier: Path): Pair<Int, Int>?

    /** Le fichier est une base SQLite qui a une table (ou une vue) `tiles`. */
    fun aUneTableTiles(fichier: Path): Boolean
}

/** Les cartes hors ligne importées par l'utilisateur : des fichiers `.mbtiles` copiés dans [dossier],
 *  pour rester disponibles quoi que devienne le fichier d'origine. */
class CartesMbtiles(
    val dossier: Path,
    private val systeme: FileSystem,
    private val lecteur: LecteurMbtiles,
) {
    private val journal = Logger.withTag("CartesMbtiles")
    private val verrou = SynchronizedObject()

    /** Les métadonnées déjà lues, par fichier et par taille : les zooms tirés de la table des tuiles
     *  demandent de la parcourir. */
    private val infos = HashMap<Pair<String, Long>, InfoMbtiles>()

    /** Les cartes importées, par nom de fichier, sans tenir compte de la casse. */
    fun liste(): List<Path> =
        (systeme.listOrNull(dossier) ?: emptyList())
            .filter { it.name.endsWith(".mbtiles", ignoreCase = true) && systeme.metadataOrNull(it)?.isRegularFile == true }
            .sortedBy { it.name.lowercase() }

    fun fichier(nom: String): Path = dossier / nom

    fun existe(nom: String): Boolean = systeme.metadataOrNull(fichier(nom))?.isRegularFile == true

    fun tailleOctets(fichier: Path): Long = systeme.metadataOrNull(fichier)?.size ?: 0L

    /** Copie la carte reçue dans le dossier, sous le nom assaini de [nomBrut] (`carte_<instant>.mbtiles`
     *  sans nom), et ferme [source]. Rend le fichier, ou null s'il n'est pas une carte MBTiles lisible.
     *
     *  La copie va dans un fichier temporaire, validé avant de prendre la place de la carte de même nom :
     *  écrire directement sur celle-ci l'écrasait, et la validation, en échec ensuite, la supprimait
     *  (constat du portage iOS de GeoMys-Android, le 17 octobre). */
    fun importer(source: Source, nomBrut: String?, instant: Long): Path? {
        val nom = nettoyerNom(nomBrut?.takeIf { it.isNotBlank() } ?: "carte_$instant.mbtiles")
        val destination = fichier(nom)
        val temporaire = dossier / "$nom.tmp"
        return try {
            systeme.createDirectories(dossier)
            // Comme sous Android et dans GeoMys-IOS : des centaines de Mo, que l'utilisateur
            // réimporte au besoin, n'ont pas leur place dans la sauvegarde iCloud.
            exclureDesSauvegardes(dossier)
            source.use { flux -> systeme.write(temporaire) { writeAll(flux) } }
            if (!lecteur.aUneTableTiles(temporaire)) {
                systeme.delete(temporaire)
                return null
            }
            try {
                systeme.atomicMove(temporaire, destination)
            } catch (_: Exception) {
                // La carte remplaçante est validée : la cible peut disparaître sans perte.
                systeme.delete(destination)
                systeme.atomicMove(temporaire, destination)
            }
            oublier(nom)
            destination
        } catch (e: Exception) {
            journal.w(e) { "import échoué : ${e.message}" }
            runCatching { systeme.delete(temporaire) }
            null
        }
    }

    fun supprimer(fichier: Path): Boolean {
        oublier(fichier.name)
        return runCatching { systeme.delete(fichier, mustExist = true) }.isSuccess
    }

    /** Nom lisible : le nom déclaré dans le fichier, sinon le nom du fichier sans son extension. */
    fun nomAffichage(fichier: Path): String =
        info(fichier).nom?.takeIf { it.isNotBlank() } ?: fichier.name.removeSuffix(".mbtiles")

    /** Les métadonnées du fichier. Faute de mieux : zooms 0 à 20 (ceux de la table des tuiles s'ils
     *  manquent), format png. */
    fun info(fichier: Path): InfoMbtiles {
        val cle = fichier.name to tailleOctets(fichier)
        synchronized(verrou) { infos[cle] }?.let { return it }
        // Ouvrir un fichier disparu créerait une base vide à sa place.
        if (systeme.metadataOrNull(fichier) == null) return InfoMbtiles(0, 20, "png", null)
        var min = 0
        var max = 20
        var format = "png"
        var nom: String? = null
        lecteur.metadonnees(fichier)?.forEach { (cleMeta, valeur) ->
            when (cleMeta.lowercase()) {
                "minzoom" -> valeur.toIntOrNull()?.let { min = it }
                "maxzoom" -> valeur.toIntOrNull()?.let { max = it }
                "format" -> format = if (valeur.startsWith("jpg") || valeur.startsWith("jpeg")) "jpg" else "png"
                "name" -> nom = valeur
            }
        }
        if (min == 0 && max == 20) lecteur.zoomsDesTuiles(fichier)?.let { (a, b) -> min = a; max = b }
        if (max < min) max = min
        val info = InfoMbtiles(min, max, format, nom)
        synchronized(verrou) { infos[cle] = info }
        return info
    }

    private fun oublier(nom: String) = synchronized(verrou) { infos.keys.removeAll { it.first == nom } }

    companion object {
        /** Nom de fichier sûr : le dernier segment du chemin, chaque caractère hors de `[A-Za-z0-9._-]`
         *  remplacé par « _ », l'extension `.mbtiles` garantie. */
        fun nettoyerNom(nom: String): String {
            val base = nom.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            return if (base.endsWith(".mbtiles", ignoreCase = true)) base else "$base.mbtiles"
        }

        // ─── Textes du dialogue des cartes ───

        const val TITRE = "Cartes hors-ligne (MBTiles)"
        const val IMPORTER = "Importer un fichier…"
        const val FERMER = "Fermer"
        const val AUCUNE = "Aucune carte importée.\n\nImportez un fichier .mbtiles depuis votre téléphone : " +
            "il apparaîtra ensuite dans le menu « fond de carte » et fonctionnera hors-ligne."
        const val CONFIRMER_SUPPRESSION = "Supprimer cette carte hors-ligne du téléphone ?"
        const val SUPPRIMER = "Supprimer"
        const val CARTE_SUPPRIMEE = "Carte supprimée"
        const val TITRE_IMPORT = "Import de la carte"
        const val COPIE = "Copie du fichier…"
        const val IMPORT_ECHOUE = "Import échoué : fichier .mbtiles invalide ou illisible"

        fun importee(nom: String) = "Carte importée : $nom"

        /** La ligne d'une carte dans la liste : « zoom 8–14 · 12 Mo ». */
        fun detail(info: InfoMbtiles, octets: Long) = "zoom ${info.zoomMin}–${info.zoomMax} · ${octets / (1024 * 1024)} Mo"

        /** Le libellé d'une carte dans le menu du fond. */
        fun libelleFond(nom: String) = "📁 $nom (hors-ligne)"
    }
}
