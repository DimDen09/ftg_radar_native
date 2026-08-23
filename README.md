# ftg_radar_native

Plugin Flutter natif de Food Truck Galaxy.

Sur Android, la version 1.4 utilise Google Play Services `GeofencingClient` :

- 99 zones trucks au maximum et une sentinelle de déplacement ;
- réception par `BroadcastReceiver`, même si le processus Flutter n'existe plus ;
- persistance locale avant réseau puis livraison/retry par WorkManager ;
- réenregistrement après `BOOT_COMPLETED` et `MY_PACKAGE_REPLACED` ;
- aucune notification foreground permanente et aucun secret serveur dans l'APK.

Le service foreground historique reste compilé temporairement pour comparaison, mais `startRadar` ne le démarre plus. Un arrêt forcé depuis les réglages Android n'est pas supporté.

## Installation FlutterFlow

```yaml
ftg_radar_native:
  git:
    url: https://github.com/DimDen09/ftg_radar_native.git
    ref: v1.4.0
```

Le Manifest du plugin fusionne automatiquement les permissions et receivers Android. Le parcours FTG doit obtenir la localisation précise et « toujours autoriser » avant l'appel :

```dart
final result = await FtgRadarNative.startRadar(
  token: radarDeviceToken,
  endpoint:
      'https://kfxfpoithlhbesuwjlkw.supabase.co/functions/v1/ftg-radar-location-sync',
);
```

Pour arrêter le Radar :

```dart
await FtgRadarNative.stopRadar();
```

Le diagnostic ne renvoie jamais le token :

```dart
final status = await FtgRadarNative.getRadarStatus();
```

La procédure d'intégration se trouve dans [docs/FLUTTERFLOW.md](docs/FLUTTERFLOW.md) et le protocole physique Android dans [docs/ANDROID_GEOFENCE_TEST.md](docs/ANDROID_GEOFENCE_TEST.md).

## Vérification

GitHub Actions exécute l'analyse Dart, les tests Flutter, les tests Kotlin, puis construit réellement l'APK Android de smoke-test. Une CI verte ne remplace pas le test physique process mort + callback + Supabase + push.
