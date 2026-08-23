# Intégration FTG dans FlutterFlow

## Dépendance

Dans les dépendances du Custom Widget `FTGSessionGateV1` :

```yaml
ftg_radar_native:
  git:
    url: https://github.com/DimDen09/ftg_radar_native.git
    ref: v1.4.0
```

Puis importer :

```dart
import 'package:ftg_radar_native/ftg_radar_native.dart';
```

Le code Kotlin et le Manifest sont fournis par le plugin. Ne pas copier les classes Android dans l'export FlutterFlow et ne pas modifier `MainActivity.kt`.

## Démarrage Android

Le démarrage reste dans le parcours utilisateur, session Supabase ouverte, compte utilisateur, Radar activé et permission de localisation précise « toujours autoriser » accordée.

1. Construire l'identifiant appareil avec l'abonnement OneSignal, ou à défaut `ftg-user-<user_id>`.
2. Appeler `ftg_issue_radar_device_token_v1` avec `p_device_id`.
3. Extraire le token retourné.
4. Appeler le plugin pendant que FTG est visible.

```dart
await FtgRadarNative.startRadar(
  token: token,
  endpoint:
      'https://kfxfpoithlhbesuwjlkw.supabase.co/functions/v1/ftg-radar-location-sync',
);
```

`startRadar` obtient une position initiale ponctuelle, demande le plan backend, puis confie les zones à `GeofencingClient`. Il ne démarre pas `RadarLocationService`.

## Arrêt et changement de compte

Avant déconnexion, passage vers un compte Pro ou désactivation du Radar :

1. Appeler `FtgRadarNative.stopRadar()`.
2. Révoquer le token avec `ftg_revoke_radar_device_token_v1`.
3. Poursuivre le changement de parcours.

Une réponse 401 ou 403 désactive aussi la configuration locale.

## Diagnostic

`FtgRadarNative.getRadarStatus()` renvoie notamment :

- `enabled` et `mode=android_geofencing_client` ;
- `foregroundServiceRequired=false` ;
- `registrationState` et le dernier échec éventuel ;
- `registeredCount`, `truckCount`, `sentinelCount` ;
- `queueDepth` ;
- `legacyForegroundServiceRunning`, qui doit rester `false`.

Le nombre et le cache local ne prouvent pas que le système surveille encore les zones. La validation finale reste le callback physique reçu alors que le processus FTG est mort.

Le token n'est jamais exposé dans ce diagnostic et aucune clé `service_role` n'est embarquée.
