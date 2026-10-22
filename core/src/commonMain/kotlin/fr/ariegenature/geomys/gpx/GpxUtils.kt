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

// Porté de GeoMys-Android @ cdd5196 (app/src/main/java/fr/ariegenature/geomys/gpx/GpxUtils.kt) : le SAX de
// javax.xml devient lireXml (LecteurXml.kt), SimpleDateFormat une lecture et une écriture sur
// kotlinx-datetime, Location.distanceBetween distanceMetres. lireBorne, qui lisait un InputStream, passe
// au sélecteur de fichier de chaque plateforme.

package fr.ariegenature.geomys.gpx

import fr.ariegenature.geomys.geo.distanceMetres
import fr.ariegenature.geomys.model.Observation
import fr.ariegenature.geomys.model.PointTrace
import fr.ariegenature.geomys.model.Sortie
import fr.ariegenature.geomys.util.maintenantMillis
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private val String.xmlEscaped: String
    get() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

fun genererGPX(observations: List<Observation>, parcours: List<PointTrace>): String {
    val sb = StringBuilder()
    sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.appendLine("""<gpx version="1.1" creator="GeoMys" xmlns="http://www.topografix.com/GPX/1/1">""")

    for (obs in observations.sortedBy { it.date }) {
        sb.appendLine("""  <wpt lat="${obs.latitude}" lon="${obs.longitude}">""")
        sb.appendLine("""    <time>${heureIso(obs.date)}</time>""")
        sb.appendLine("""    <name>${obs.espece.xmlEscaped}</name>""")
        var desc = if (obs.nombre > 1) "${obs.nombre} individus" else "1 individu"
        if (obs.notes.isNotEmpty()) desc += " — ${obs.notes.xmlEscaped}"
        sb.appendLine("""    <desc>$desc</desc>""")
        sb.appendLine("""    <sym>Waypoint</sym>""")
        sb.appendLine("""    <type>Observation ornithologique</type>""")
        sb.appendLine("""  </wpt>""")
    }

    if (parcours.isNotEmpty()) {
        sb.appendLine("""  <trk>""")
        sb.appendLine("""    <name>Parcours GeoMys</name>""")
        sb.appendLine("""    <trkseg>""")
        for (pt in parcours) {
            sb.appendLine("""      <trkpt lat="${pt.latitude}" lon="${pt.longitude}"/>""")
        }
        sb.appendLine("""    </trkseg>""")
        sb.appendLine("""  </trk>""")
    }

    sb.appendLine("</gpx>")
    return sb.toString()
}

/** Taille maximale d'un fichier GPX importé (20 Mo) : au-delà, refus avant lecture en mémoire. */
const val TAILLE_MAX_GPX: Long = 20L * 1024 * 1024

fun importerGPX(data: ByteArray): Sortie? {
    if (data.size > TAILLE_MAX_GPX) return null
    val handler = GPXHandler()
    return try {
        lireXml(decoderXml(data), handler)
        handler.buildSortie()
    } catch (e: Exception) {
        null
    }
}

/** `yyyy-MM-dd'T'HH:mm:ss'Z'`, en UTC (le format GPX tronque les millisecondes). */
private fun heureIso(millis: Long): String {
    val t = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC)
    fun deux(v: Int) = v.toString().padStart(2, '0')
    return "${t.year.toString().padStart(4, '0')}-${deux(t.month.ordinal + 1)}-${deux(t.day)}" +
        "T${deux(t.hour)}:${deux(t.minute)}:${deux(t.second)}Z"
}

private val HEURE_ISO = Regex("""^(\d+)-(\d+)-(\d+)T(\d+):(\d+):(\d+)Z""")

/** Lecture de `yyyy-MM-dd'T'HH:mm:ss'Z'` (UTC) comme SimpleDateFormat en mode tolérant : un
 *  préfixe suffit, les chiffres ne sont pas comptés, et les débordements se reportent (le 30
 *  février donne le 2 mars). Null si le texte ne commence pas par ce motif : une heure avec
 *  millisecondes ou avec un décalage horaire n'était pas lue non plus. */
private fun lireHeureIso(texte: String): Long? {
    val m = HEURE_ISO.find(texte) ?: return null
    val v = m.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    return try {
        val jour = LocalDate(v[0], 1, 1).plus(v[1] - 1, DateTimeUnit.MONTH).plus(v[2] - 1, DateTimeUnit.DAY)
        val secondes = v[3].toLong() * 3600 + v[4].toLong() * 60 + v[5]
        jour.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + secondes * 1000
    } catch (e: IllegalArgumentException) {
        null
    }
}

private class GPXHandler : GestionnaireXml {
    private val trkpts = mutableListOf<PointTrace>()
    private val wpts = mutableListOf<WptData>()

    private var inWpt = false
    private var inTrkpt = false
    private var currentLat: Double? = null
    private var currentLon: Double? = null
    private var currentTime: Long? = null
    private var currentName = ""
    private var currentDesc = ""
    private val currentText = StringBuilder()
    private var premiereDate: Long? = null

    data class WptData(val lat: Double, val lon: Double, val time: Long?, val name: String, val desc: String)

    override fun debut(nom: String, attributs: Map<String, String>) {
        currentText.clear()
        when (nom) {
            "wpt" -> {
                inWpt = true
                currentLat = attributs["lat"]?.toDoubleOrNull()
                currentLon = attributs["lon"]?.toDoubleOrNull()
                currentTime = null; currentName = ""; currentDesc = ""
            }
            "trkpt" -> {
                inTrkpt = true
                currentLat = attributs["lat"]?.toDoubleOrNull()
                currentLon = attributs["lon"]?.toDoubleOrNull()
            }
        }
    }

    override fun caracteres(texte: String) {
        currentText.append(texte)
    }

    override fun fin(nom: String) {
        val text = currentText.toString().trim()
        if (inWpt) {
            when (nom) {
                "name" -> currentName = text
                "desc" -> currentDesc = text
                "time" -> currentTime = lireHeureIso(text)
                "wpt" -> {
                    val lat = currentLat ?: return
                    val lon = currentLon ?: return
                    wpts.add(WptData(lat, lon, currentTime, currentName, currentDesc))
                    inWpt = false
                }
            }
        } else if (inTrkpt) {
            when (nom) {
                "time" -> if (premiereDate == null) premiereDate = lireHeureIso(text)
                "trkpt" -> {
                    val lat = currentLat ?: return
                    val lon = currentLon ?: return
                    trkpts.add(PointTrace(lat, lon))
                    inTrkpt = false
                }
            }
        }
    }

    fun buildSortie(): Sortie? {
        if (trkpts.isEmpty() && wpts.isEmpty()) return null
        var distance = 0.0
        var prev: PointTrace? = null
        for (pt in trkpts) {
            if (prev != null) {
                distance += distanceMetres(prev.latitude, prev.longitude, pt.latitude, pt.longitude)
            }
            prev = pt
        }
        val observations = wpts.map { wpt ->
            var nombre = 1
            var notes = wpt.desc
            val match = Regex("""^(\d+) individu""").find(wpt.desc)
            if (match != null) {
                val parts = wpt.desc.split(" — ")
                nombre = parts[0].split(" ").firstOrNull()?.toIntOrNull() ?: 1
                notes = if (parts.size > 1) parts.drop(1).joinToString(" — ") else ""
            }
            Observation(
                espece = wpt.name.ifEmpty { "Espèce inconnue" },
                latitude = wpt.lat, longitude = wpt.lon,
                date = wpt.time ?: premiereDate ?: maintenantMillis(),
                notes = notes, nombre = nombre
            )
        }
        val dateSortie = premiereDate ?: wpts.mapNotNull { it.time }.minOrNull() ?: maintenantMillis()
        return Sortie(date = dateSortie, pointsParcours = trkpts, observations = observations, distanceTotale = distance)
    }
}
