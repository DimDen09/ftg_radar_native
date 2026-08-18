FTG RADAR NATIVE V24 — FLUTTERFLOW PLUGIN

Ce pack est la version adaptée à l'interface FlutterFlow web.
Il ne demande PAS d'éditer MainActivity.kt.
Il ne demande PAS de créer RadarLocationService.kt dans FlutterFlow.
Le Kotlin est embarqué dans le plugin Git et fusionné automatiquement au build Android.

Contenu :
- pubspec.yaml
- lib/ftg_radar_native.dart
- android/src/main/AndroidManifest.xml
- android/src/main/kotlin/com/foodtruckgalaxy/ftg_radar_native/FtgRadarNativePlugin.kt
- android/src/main/kotlin/com/foodtruckgalaxy/ftg_radar_native/RadarLocationService.kt

À faire ensuite :
1. Mettre CE DOSSIER tel quel dans un dépôt GitHub public (ou privé avec token).
2. Dans FlutterFlow > Custom Code > FTGSessionGateV1, ajouter comme dépendance Git :

ftg_radar_native:
  git:
    url: https://github.com/VOTRE_COMPTE/ftg_radar_native.git

3. Remplacer tout le code du widget FTGSessionGateV1 par FTGSessionGateV1_V24_FINAL_PLUGIN_RADAR.dart.
4. Save > Compile.
5. Aucun MainActivity.kt à modifier dans FlutterFlow.
6. Le manifest du plugin ajoute automatiquement le service et les permissions nécessaires lors du build.
