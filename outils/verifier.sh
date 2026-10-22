#!/bin/bash
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

# Vérification locale, à lancer avant de pousser : elle tient lieu d'intégration continue.
#   1. compile l'appli Android (APK de debug) ;
#   2. lance les tests communs, sur la JVM et sur un simulateur iOS, et ceux des écrans sur la JVM
#      (règles) et sur le simulateur (mise en page) ;
#   3. compile l'appli iOS pour le simulateur.
#
# Usage : outils/verifier.sh [--sans-ios] [--simulateur=<UDID>]
#   --sans-ios           sans Xcode : APK Android et tests sur la JVM seulement.
#   --simulateur=<UDID>  les tests iOS sur ce simulateur plutôt que sur celui que choisit le plugin
#                        Kotlin (propriété geomys.simulateur de core/ et ui/build.gradle.kts).
#
# Les tests iOS tournent sur un simulateur démarré sans écran puis arrêté par Gradle ;
# un simulateur déjà ouvert n'est pas touché. Le build iOS ne vise aucun simulateur précis.

set -euo pipefail

cd "$(dirname "$0")/.."

# xcode-select peut pointer vers les outils en ligne de commande, qui ne suffisent pas.
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"

avec_ios=1
simulateur=""
for argument in "$@"; do
    case "$argument" in
        --sans-ios) avec_ios=0 ;;
        --simulateur=?*) simulateur="-Pgeomys.simulateur=${argument#--simulateur=}" ;;
        *) echo "Option inconnue : $argument" >&2; exit 2 ;;
    esac
done

debut=$SECONDS
etape() { printf '\n\033[1;36m== %s\033[0m\n' "$1"; }

if [ "$avec_ios" -eq 1 ]; then
    etape "Android : APK de debug ; tests communs et tests des écrans, sur la JVM et le simulateur iOS"
    ./gradlew :androidApp:assembleDebug :core:allTests :ui:testAndroidHostTest :ui:iosSimulatorArm64Test \
        ${simulateur:+"$simulateur"} --console=plain --quiet

    etape "iOS : appli pour le simulateur"
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
        -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
        -derivedDataPath build/verifier/ios -quiet build
else
    etape "Android : APK de debug ; tests communs et tests des écrans sur la JVM"
    ./gradlew :androidApp:assembleDebug :core:testAndroidHostTest :ui:testAndroidHostTest --console=plain --quiet
fi

printf '\n\033[1;32mTout est bon\033[0m (%d s).\n' "$((SECONDS - debut))"
