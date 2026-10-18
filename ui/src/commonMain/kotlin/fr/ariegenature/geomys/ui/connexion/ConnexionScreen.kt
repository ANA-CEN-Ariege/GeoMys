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

package fr.ariegenature.geomys.ui.connexion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.ariegenature.geomys.ServicesGeoNature
import fr.ariegenature.geomys.network.VerificationConnexion
import fr.ariegenature.geomys.ui.theme.CouleursGeoMys
import fr.ariegenature.geomys.ui.theme.FondGeoMys
import kotlinx.coroutines.launch

/** Écran provisoire du lot 1 : connexion à un serveur GeoNature, pour vérifier le critère de
 *  sortie du lot (connexion et authentification) avant l'écran de configuration du lot 5. Le
 *  résultat s'affiche et part aussi dans le journal, sur une ligne `CONNEXION_GEONATURE` (sans le
 *  mot de passe).
 *
 *  [lancement] permet les essais automatisés : `url`, `login`, `mdp` pré-remplissent les champs,
 *  `action=tester` lance la vérification. */
@Composable
fun ConnexionScreen(services: ServicesGeoNature, onRetour: () -> Unit, lancement: Map<String, String>) {
    val config = services.config
    val portee = rememberCoroutineScope()
    var url by remember { mutableStateOf(lancement["url"] ?: config.urlServeur) }
    var login by remember { mutableStateOf(lancement["login"] ?: config.login) }
    var motDePasse by remember { mutableStateOf(lancement["mdp"] ?: config.motDePasse) }
    var enCours by remember { mutableStateOf(false) }
    var resultat by remember { mutableStateOf<VerificationConnexion.Resultat?>(null) }

    fun tester() {
        if (enCours) return
        config.urlServeur = url.trim()
        config.login = login.trim()
        config.motDePasse = motDePasse
        enCours = true
        portee.launch {
            try {
                val r = services.verificationConnexion.verifier(config)
                println(r.enLigne())
                resultat = r
            } finally {
                enCours = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (lancement["action"] == "tester") tester()
    }

    FondGeoMys {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRetour) { Text("← Accueil", color = CouleursGeoMys.jauneClair) }
                Text("Connexion GeoNature", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Panneau {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL du serveur") },
                    placeholder = { Text("https://geonature.exemple.fr") },
                    singleLine = true,
                    keyboardOptions = SAISIE_BRUTE.copy(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Identifiant") },
                    singleLine = true,
                    keyboardOptions = SAISIE_BRUTE.copy(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = motDePasse,
                    onValueChange = { motDePasse = it },
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = SAISIE_BRUTE.copy(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { tester() }, enabled = !enCours) {
                    Text(if (enCours) "Connexion…" else "Tester la connexion", color = CouleursGeoMys.jauneClair)
                }
            }
            resultat?.let { r ->
                Panneau {
                    Text(r.message, color = if (r.reussi) Color(0xFF9CCC65) else CouleursGeoMys.orange)
                    if (r.version.isNotEmpty()) Text("Serveur GeoNature v${r.version}", color = Color.White)
                    if (r.reussi) Text("Utilisateur : ${r.utilisateur.ifEmpty { "—" }} (id_role ${r.idRole})", color = Color.White)
                    r.appelAuthentifie?.let {
                        Text(
                            "Appel authentifié (/api/gn_commons/modules) : HTTP $it",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/** Ni majuscule ni correction automatiques : le clavier d'iOS change sinon `cgross` en `Cgross`, et
 *  GeoNature compare l'identifiant à la lettre près. */
private val SAISIE_BRUTE = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)

@Composable
private fun Panneau(contenu: @Composable () -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.55f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { contenu() }
    }
}
