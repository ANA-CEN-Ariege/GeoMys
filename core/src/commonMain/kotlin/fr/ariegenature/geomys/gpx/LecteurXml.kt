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

// Propre au portage : GeoMys-Android lisait les GPX avec l'analyseur SAX de javax.xml, qui n'existe
// pas en Kotlin commun. Cette lecture en reprend ce que l'import GPX utilise, et les mêmes refus.

package fr.ariegenature.geomys.gpx

/** Ce que reçoit un lecteur XML, dans l'ordre du document, comme un DefaultHandler SAX : les noms
 *  sont les noms qualifiés (préfixe compris). */
internal interface GestionnaireXml {
    fun debut(nom: String, attributs: Map<String, String>)

    fun caracteres(texte: String)

    fun fin(nom: String)
}

/** XML refusé : mal formé, ou porteur d'une déclaration DOCTYPE. */
internal class XmlRefuse(message: String) : Exception(message)

/**
 * Lecture XML minimale, à la manière d'un analyseur SAX :
 * - éléments, attributs, texte, sections CDATA ; commentaires, déclaration XML et instructions de
 *   traitement ignorés ;
 * - entités prédéfinies (`&amp;`…) et numériques (`&#233;`, `&#xE9;`), toute autre entité refusée ;
 * - comme l'analyseur durci de GeoMys-Android, une déclaration DOCTYPE est refusée (expansion
 *   d'entités, entités externes) ;
 * - un document mal formé (balises croisées ou non fermées, texte hors de la racine, plusieurs
 *   racines) lève [XmlRefuse].
 */
internal fun lireXml(document: String, gestionnaire: GestionnaireXml) {
    LecteurXml(document, gestionnaire).lire()
}

/** Décode les octets d'un document XML : UTF-8 (marque d'ordre éventuelle retirée), UTF-16 si une
 *  marque l'annonce, ou ISO-8859-1 si la déclaration XML le dit. Une suite UTF-8 invalide lève
 *  [XmlRefuse], comme l'analyseur d'Android. */
internal fun decoderXml(octets: ByteArray): String {
    fun octet(i: Int) = octets[i].toInt() and 0xFF
    if (octets.size >= 2 && octet(0) == 0xFE && octet(1) == 0xFF) return utf16(octets, 2, grandBoutiste = true)
    if (octets.size >= 2 && octet(0) == 0xFF && octet(1) == 0xFE) return utf16(octets, 2, grandBoutiste = false)
    val debut = if (octets.size >= 3 && octet(0) == 0xEF && octet(1) == 0xBB && octet(2) == 0xBF) 3 else 0
    val declaration = buildString {
        var i = debut
        while (i < octets.size && i < debut + 200 && octet(i) < 0x80) {
            append(octet(i).toChar())
            if (endsWith("?>")) break
            i++
        }
    }
    val encodage = Regex("""^<\?xml[^>]*encoding\s*=\s*["']([^"']+)["']""").find(declaration)?.groupValues?.get(1)?.lowercase()
    if (encodage in ENCODAGES_LATIN1) {
        return buildString(octets.size - debut) { for (i in debut until octets.size) append(octet(i).toChar()) }
    }
    return try {
        octets.decodeToString(debut, octets.size, throwOnInvalidSequence = true)
    } catch (e: CharacterCodingException) {
        throw XmlRefuse("UTF-8 invalide")
    }
}

private val ENCODAGES_LATIN1 = setOf("iso-8859-1", "iso8859-1", "latin1", "latin-1", "l1")

private fun utf16(octets: ByteArray, debut: Int, grandBoutiste: Boolean): String = buildString {
    var i = debut
    while (i + 1 < octets.size) {
        val a = octets[i].toInt() and 0xFF
        val b = octets[i + 1].toInt() and 0xFF
        append((if (grandBoutiste) (a shl 8) or b else (b shl 8) or a).toChar())
        i += 2
    }
}

private class LecteurXml(private val doc: String, private val gestionnaire: GestionnaireXml) {
    private var i = 0
    private val pile = ArrayDeque<String>()
    private var racineVue = false

    fun lire() {
        while (i < doc.length) {
            if (doc[i] == '<') balise() else texte()
        }
        if (!racineVue) refuser("aucun élément")
        if (pile.isNotEmpty()) refuser("élément <${pile.last()}> non fermé")
    }

    private fun refuser(raison: String): Nothing = throw XmlRefuse(raison)

    private fun commencePar(prefixe: String) = doc.startsWith(prefixe, i)

    private fun jusqua(fin: String, depuis: Int): Int {
        val k = doc.indexOf(fin, depuis)
        if (k < 0) refuser("« $fin » attendu")
        return k
    }

    private fun texte() {
        val fin = doc.indexOf('<', i).let { if (it < 0) doc.length else it }
        val brut = doc.substring(i, fin)
        if (pile.isEmpty()) {
            if (brut.isNotBlank()) refuser("texte hors de l'élément racine")
        } else {
            gestionnaire.caracteres(decoderEntites(brut))
        }
        i = fin
    }

    private fun balise() {
        when {
            commencePar("<?") -> i = jusqua("?>", i + 2) + 2
            commencePar("<!--") -> i = jusqua("-->", i + 4) + 3
            commencePar("<![CDATA[") -> {
                val fin = jusqua("]]>", i + 9)
                if (pile.isEmpty()) refuser("CDATA hors de l'élément racine")
                gestionnaire.caracteres(doc.substring(i + 9, fin))
                i = fin + 3
            }
            commencePar("<!DOCTYPE") -> refuser("DOCTYPE refusé")
            commencePar("<!") -> refuser("déclaration inattendue")
            commencePar("</") -> {
                val fin = jusqua(">", i + 2)
                val nom = doc.substring(i + 2, fin).trim()
                if (pile.isEmpty() || pile.removeLast() != nom) refuser("fermeture </$nom> inattendue")
                gestionnaire.fin(nom)
                i = fin + 1
            }
            else -> ouverture()
        }
    }

    private fun ouverture() {
        i++
        val nom = nom()
        if (nom.isEmpty()) refuser("nom d'élément attendu")
        val attributs = LinkedHashMap<String, String>()
        while (true) {
            espaces()
            if (i >= doc.length) refuser("balise <$nom non terminée")
            when {
                commencePar("/>") -> {
                    i += 2
                    ouvrir(nom, attributs)
                    gestionnaire.fin(nom)
                    return
                }
                doc[i] == '>' -> {
                    i++
                    ouvrir(nom, attributs)
                    pile.addLast(nom)
                    return
                }
                else -> {
                    val cle = nom()
                    if (cle.isEmpty()) refuser("attribut attendu dans <$nom>")
                    espaces()
                    if (i >= doc.length || doc[i] != '=') refuser("« = » attendu après $cle")
                    i++
                    espaces()
                    val guillemet = doc.getOrNull(i)
                    if (guillemet != '"' && guillemet != '\'') refuser("valeur de $cle sans guillemets")
                    val fin = jusqua(guillemet.toString(), i + 1)
                    val brut = doc.substring(i + 1, fin)
                    if ('<' in brut) refuser("« < » dans la valeur de $cle")
                    if (attributs.put(cle, decoderEntites(brut)) != null) refuser("attribut $cle en double")
                    i = fin + 1
                }
            }
        }
    }

    private fun ouvrir(nom: String, attributs: Map<String, String>) {
        if (pile.isEmpty()) {
            if (racineVue) refuser("plusieurs éléments racines")
            racineVue = true
        }
        gestionnaire.debut(nom, attributs)
    }

    private fun nom(): String {
        val debut = i
        while (i < doc.length && !doc[i].isWhitespace() && doc[i] != '/' && doc[i] != '>' && doc[i] != '=') i++
        return doc.substring(debut, i)
    }

    private fun espaces() {
        while (i < doc.length && doc[i].isWhitespace()) i++
    }

    private fun decoderEntites(brut: String): String {
        if ('&' !in brut) return brut
        val sb = StringBuilder(brut.length)
        var k = 0
        while (k < brut.length) {
            val c = brut[k]
            if (c != '&') {
                sb.append(c)
                k++
                continue
            }
            val fin = brut.indexOf(';', k)
            if (fin < 0) refuser("entité non terminée")
            val nom = brut.substring(k + 1, fin)
            when {
                nom == "amp" -> sb.append('&')
                nom == "lt" -> sb.append('<')
                nom == "gt" -> sb.append('>')
                nom == "quot" -> sb.append('"')
                nom == "apos" -> sb.append('\'')
                nom.startsWith("#x") -> sb.appendCodePoint(nom.substring(2).toIntOrNull(16))
                nom.startsWith("#") -> sb.appendCodePoint(nom.substring(1).toIntOrNull())
                else -> refuser("entité &$nom; inconnue")
            }
            k = fin + 1
        }
        return sb.toString()
    }

    private fun StringBuilder.appendCodePoint(code: Int?) {
        if (code == null || code < 0 || code > 0x10FFFF) refuser("référence de caractère invalide")
        if (code < 0x10000) {
            append(code.toChar())
        } else {
            val v = code - 0x10000
            append((0xD800 + (v shr 10)).toChar())
            append((0xDC00 + (v and 0x3FF)).toChar())
        }
    }
}
