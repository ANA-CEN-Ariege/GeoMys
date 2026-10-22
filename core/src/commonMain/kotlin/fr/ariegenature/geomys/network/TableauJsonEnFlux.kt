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

// Remplace la lecture en flux par le JsonReader de Gson (GeoMys-Android @ cdd5196,
// network/GeoNatureBrowse.kt, lireDatasetsStream et lireHabitatsStream) : kotlinx-serialization lit
// le tableau élément par élément depuis le tampon okio.

package fr.ariegenature.geomys.network

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okio.BufferedSource

/** Les éléments du tableau JSON de [source], À LA RACINE ou enveloppé sous l'une des [cles] d'un
 *  objet (`data`, `items`, `results`). Chaque élément est lu seul, passé à [convertir] (null =
 *  écarté) puis oublié : ni la réponse entière en chaîne, ni son arbre JSON complet en mémoire.
 *
 *  Comme sous Android, les clés sont lues dans l'ordre du document, jusqu'à ce qu'un tableau donne
 *  au moins un élément ; une racine ni tableau ni objet donne une liste vide. Écart : sous l'une
 *  des [cles], une valeur ni tableau ni null fait échouer la lecture, quand Gson la sautait.
 *
 *  @throws SerializationException si le JSON est invalide (page HTML d'un portail captif…). */
@OptIn(ExperimentalSerializationApi::class)
internal fun <T : Any> lireTableauEnFlux(
    source: BufferedSource,
    cles: Set<String>,
    convertir: (JsonElement) -> T?,
): List<T> {
    val resultat = mutableListOf<T>()
    when (premierCaractere(source)) {
        '[' -> JSON_FLUX.decodeFromBufferedSource(ElementsUnParUn(resultat, convertir), source)
        '{' -> JSON_FLUX.decodeFromBufferedSource(Enveloppe(cles, resultat, convertir), source)
        else -> verifierValeurSimple(JSON_FLUX.decodeFromBufferedSource(JsonElement.serializer(), source))
    }
    return resultat
}

/** `ignoreUnknownKeys` : dans l'enveloppe, les clés autres que [cles] sont sautées. */
private val JSON_FLUX = Json { ignoreUnknownKeys = true }

/** Une racine ni tableau ni objet doit au moins être du JSON valide, comme sous Gson : le lecteur
 *  d'arbre de kotlinx accepte un littéral sans guillemets (`<html>`), refusé donc ici. */
private fun verifierValeurSimple(valeur: JsonElement) {
    val litteral = valeur as? JsonPrimitive ?: return
    if (litteral is JsonNull || litteral.isString) return
    val contenu = litteral.content
    if (contenu != "true" && contenu != "false" && !NOMBRE_JSON.matches(contenu)) {
        throw SerializationException("Valeur JSON invalide : ${contenu.take(40)}")
    }
}

private val NOMBRE_JSON = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

private val DESCRIPTEUR_TABLEAU: SerialDescriptor = ListSerializer(JsonElement.serializer()).descriptor

/** Premier caractère significatif de [source], sans le consommer. */
private fun premierCaractere(source: BufferedSource): Char? {
    val apercu = source.peek()
    while (!apercu.exhausted()) {
        val c = apercu.readByte().toInt().toChar()
        if (c != ' ' && c != '\n' && c != '\r' && c != '\t') return c
    }
    return null
}

/** Un tableau lu élément par élément ; [convertir] null : éléments lus puis jetés. */
private class ElementsUnParUn<T : Any>(
    private val resultat: MutableList<T>,
    private val convertir: ((JsonElement) -> T?)?,
) : DeserializationStrategy<Unit> {
    override val descriptor: SerialDescriptor = DESCRIPTEUR_TABLEAU

    override fun deserialize(decoder: Decoder) {
        decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                val element = decodeSerializableElement(descriptor, index, JsonElement.serializer())
                convertir?.invoke(element)?.let(resultat::add)
            }
        }
    }
}

/** Un objet dont on lit les tableaux sous [cles], dans l'ordre du document, tant que [resultat]
 *  est vide. Une valeur null sous l'une des clés est sautée. */
@OptIn(ExperimentalSerializationApi::class)
private class Enveloppe<T : Any>(
    cles: Set<String>,
    private val resultat: MutableList<T>,
    private val convertir: (JsonElement) -> T?,
) : DeserializationStrategy<Unit> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Enveloppe") {
        cles.forEach { element(it, DESCRIPTEUR_TABLEAU, isOptional = true) }
    }

    override fun deserialize(decoder: Decoder) {
        decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                val tableau = ElementsUnParUn(resultat, convertir.takeIf { resultat.isEmpty() })
                decodeNullableSerializableElement(descriptor, index, tableau)
            }
        }
    }
}
