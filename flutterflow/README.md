# Bridge FlutterFlow FTG

Le fichier `ftg_radar_bridge.dart` contient uniquement la liaison ciblée à intégrer au `FTGSessionGateV1` courant. Il ne remplace pas le widget complet et n'embarque aucun secret.

Avant `ftgStartNativeRadar`, le parcours doit vérifier la session Supabase, le type de compte `user`, l'activation du Radar et `LocationPermission.always`. Avant déconnexion, passage Pro ou désactivation du Radar, appeler `ftgStopNativeRadar`.

Les réglages Android et iOS requis sont détaillés dans `docs/FLUTTERFLOW.md`.
