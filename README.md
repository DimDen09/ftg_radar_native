# ftg_radar_native

Plugin Flutter natif de Food Truck Galaxy pour maintenir le Radar utilisateur actif en arrière-plan sur Android.

Le plugin démarre un service de localisation Android au premier plan, affiche une notification persistante, transmet les positions à l'endpoint FTG en HTTPS et survit à la fermeture de l'interface depuis les applications récentes. Android reste libre d'arrêter une application forcée depuis les réglages ou soumise à une politique d'économie d'énergie du constructeur.

## Installation FlutterFlow

Ajoutez cette dépendance Git dans les dépendances du Custom Widget `FTGSessionGateV1` :

```yaml
ftg_radar_native:
  git:
    url: https://github.com/DimDen09/ftg_radar_native.git
    ref: main
```

Le manifest du plugin fusionne automatiquement le service et les permissions Android nécessaires. Le parcours FTG doit obtenir l'autorisation de localisation « toujours autoriser » avant d'appeler le plugin.

```dart
final result = await FtgRadarNative.startRadar(
  token: radarDeviceToken,
  endpoint:
      'https://kfxfpoithlhbesuwjlkw.supabase.co/functions/v1/ftg-radar-location-sync',
);
```

Pour arrêter le Radar à la déconnexion ou avant un parcours Pro :

```dart
await FtgRadarNative.stopRadar();
```

Le diagnostic natif ne renvoie jamais le token :

```dart
final status = await FtgRadarNative.getRadarStatus();
```

La procédure FTG complète est décrite dans [docs/FLUTTERFLOW.md](docs/FLUTTERFLOW.md).

## Vérification

Le workflow GitHub Actions exécute l'analyse Dart, les tests Flutter, les tests Kotlin et un vrai build APK Android. L'APK de démonstration est publié comme artefact du workflow.

