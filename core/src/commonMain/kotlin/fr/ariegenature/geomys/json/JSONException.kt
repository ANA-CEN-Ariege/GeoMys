/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifié pour GeoMys-Common (ANA - CEN Ariège, 2026) : traduit de Java en Kotlin multiplateforme
 * depuis les sources du SDK Android (android-36.1, org/json/JSONException.java). Texte de la
 * licence : licences/Apache-2.0.txt.
 */

package fr.ariegenature.geomys.json

/** Erreur de lecture ou d'écriture JSON. Comme sous Android, ce n'est pas une erreur réseau : le
 *  code de repli hors ligne, qui rattrape les `IOException`, ne la confond pas avec elles. */
class JSONException : Exception {
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
