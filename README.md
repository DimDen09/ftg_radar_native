# ftg_radar_native

Plugin Flutter natif de Food Truck Galaxy pour maintenir le Radar utilisateur actif en arrière-plan sur Android et iOS.

Sur Android, le plugin démarre un service de localisation au premier plan, affiche une notification persistante, transmet les positions à l'endpoint FTG en HTTPS et survit à la fermeture de l'interface depuis les applications récentes. Android reste libre d'arrêter une application forcée depuis les réglages ou soumise à une politique d'économie d'énergie du constructeur.

Sur iOS, le plugin utilise Core Location avec le mode d'arrière-plan `location`. Le suivi continue lorsque l'application passe en arrière-plan, mais Apple ne garantit pas sa relance après une fermeture forcée par l'utilisateur.

## Installation FlutterFlow

Ajoutez cette dépendance Git dans les dépendances du Custom Widget `FTGSessionGateV1` :

```yaml
ftg_radar_native:
  git:
    url: https://github.com/DimDen09/ftg_radar_native.git
    ref: aea27078522c54e4b7170a67157158ddb7048e26
```

Le manifest du plugin fusionne automatiquement le service et les permissions Android nécessaires. Sur iOS, le host doit déclarer les descriptions de permission et `UIBackgroundModes/location`. Sur les deux plateformes, le parcours FTG doit obtenir l'autorisation de localisation « toujours autoriser » avant d'appeler le plugin.

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

Les workflows GitHub Actions exécutent l'analyse Dart, les tests Flutter, les tests Kotlin, un vrai build APK Android et un vrai build iOS pour simulateur sans signature. Les applications de démonstration sont publiées comme artefacts.
