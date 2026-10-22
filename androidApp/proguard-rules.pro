# Règles R8 de la release de GeoMys (androidApp/build.gradle.kts).
#
# R8 retire le code inutilisé et l'optimise, mais ne renomme rien : des messages montrés à l'utilisateur
# citent le nom de classe d'une exception (« Chargement interrompu : … », erreurs réseau), et les traces
# d'erreur restent lisibles sans fichier de correspondance. Les bibliothèques apportent leurs propres
# règles (kotlinx-serialization, Ktor, Compose, MapLibre et son JavaCPP).
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
