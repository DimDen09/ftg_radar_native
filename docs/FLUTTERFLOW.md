# Intégration FTG dans FlutterFlow

## Dépendance

Dans les dépendances du Custom Widget `FTGSessionGateV1`, ajouter :

```yaml
ftg_radar_native:
  git:
    url: https://github.com/DimDen09/ftg_radar_native.git
    ref: main
```

Puis importer :

```dart
import 'package:ftg_radar_native/ftg_radar_native.dart';
```

Le code Kotlin et le manifest sont fournis par le plugin. Il ne faut pas copier `RadarLocationService.kt` dans le projet FlutterFlow ni modifier `MainActivity.kt`.

## Démarrage Android

Le démarrage doit rester dans le parcours utilisateur, une fois la session Supabase ouverte et après obtention de `LocationPermission.always` avec Geolocator.

1. Construire l'identifiant appareil avec l'identifiant d'abonnement OneSignal, ou à défaut `ftg-user-<user_id>`.
2. Appeler la RPC Supabase `ftg_issue_radar_device_token_v1` avec `p_device_id`.
3. Extraire le champ `token` non vide de la réponse.
4. Appeler `FtgRadarNative.startRadar` avec ce token et l'endpoint ci-dessous.

```dart
const endpoint =
    'https://kfxfpoithlhbesuwjlkw.supabase.co/functions/v1/ftg-radar-location-sync';

await FtgRadarNative.startRadar(
  token: token,
  endpoint: endpoint,
);
```

Le démarrage doit être déclenché pendant que l'application est visible. Sur Android 11 et versions suivantes, si Android ne propose d'abord que l'autorisation « pendant l'utilisation », le parcours FTG doit guider l'utilisateur vers l'autorisation en arrière-plan avant de lancer le service.

## Arrêt et changement de compte

Avant toute déconnexion, bascule vers un compte Pro ou changement de compte :

1. Appeler `FtgRadarNative.stopRadar()`.
2. Appeler la RPC `ftg_revoke_radar_device_token_v1` avec le même `p_device_id`.
3. Poursuivre la déconnexion ou le changement de parcours.

Une réponse 401 ou 403 de l'endpoint arrête également le service et efface sa configuration locale.

## Diagnostic

`FtgRadarNative.getRadarStatus()` renvoie :

- `running` : état réel du service dans le processus courant ;
- `state` : `starting`, `running`, `stopping`, `stopped` ou `start_failed` ;
- `lastSyncAt` : date UTC de la dernière réponse 2xx ;
- `lastHttpStatus` : dernier code HTTP ;
- `lastError` : dernier diagnostic non sensible.

Le token n'est jamais inclus dans ce diagnostic.

