# Validation physique Android du Radar FTG

Package FTG : `com.mycompany.myfoodtruck`.

## Capturer les preuves

Dans PowerShell, téléphone relié en USB avec le débogage activé :

```powershell
adb devices
adb logcat -c
adb logcat -s 'FTG_RADAR_GEOFENCE:I' '*:S'
```

Après activation du Radar, attendre une ligne `registration_success` avec `total` inférieur ou égal à 100, `trucks` inférieur ou égal à 99 et `sentinel=1`.

## Prouver la disparition du mécanisme historique

Swiper FTG, attendre la disparition de « Radar FTG actif », puis lancer :

```powershell
adb shell pidof com.mycompany.myfoodtruck
adb shell dumpsys activity services com.mycompany.myfoodtruck | Select-String RadarLocationService
adb shell dumpsys notification --noredact | Select-String 'Radar FTG actif'
```

Les trois commandes ne doivent rien retourner. Ne pas utiliser « Forcer l'arrêt » dans les réglages : ce cas est volontairement hors périmètre Android Geofencing.

## Test décisif

1. Processus et service morts, entrer physiquement dans une zone truck avec le truck ouvert.
2. Relever `callback_received`, `transition type=truck_enter`, `event_persisted` puis `backend_delivery` dans Logcat.
3. Vérifier dans Supabase les événements `truck_enter` et `backend_delivery` avec `is_service_running=false`.
4. Vérifier la réception du push FTG sans rouvrir l'application.
5. Refaire après plusieurs dizaines de minutes.
6. Sortir physiquement de la sentinelle et relever `sentinel_exit`, `rearm_success`, puis les événements Supabase `sentinel_exit`, `backend_delivery` et `rearm`.
7. Redémarrer le téléphone, sans ouvrir FTG, et relever `restore_started`, `restore_success`, `boot_reregister` et `rearm`.
8. Refaire entièrement sur le Huawei puis sur un second Android.

Un cache local ou une ligne `registration_success` prouve uniquement que l'appel `addGeofences` a réussi. Seul le callback physique reçu processus mort, livré à Supabase et suivi du push valide le Radar.
