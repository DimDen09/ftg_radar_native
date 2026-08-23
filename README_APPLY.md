# FTG Radar native 1.4.1 candidate ? local alert primary

## Backend already applied in production
- `send-onesignal-push` v52: Radar `user_radar_crossed` and `radar_truck_opened` use `ttl=0`.
- `ftg-radar-location-sync` v6: accepts `local_notification_shown` and requests push suppression only for `truck_enter` when the local alert was actually shown.
- `ftg_build_radar_geofence_plan_v1`: returns `truck_name` and registers only `open_filter=true` non-favorite compatible trucks.
- `ftg_notification_requires_push_v1`: honors `data.suppress_push=true`.
- session trigger injects `suppress_push=true` for local-primary movement alerts.
- favorite CLOSED -> OPEN trigger creates a fresh favorite notification/job even inside an existing all-day slot.

## Native files to replace/add in ftg_radar_native
Replace:
- android/src/main/kotlin/com/foodtruckgalaxy/ftg_radar_native/RadarGeofenceModels.kt
- android/src/main/kotlin/com/foodtruckgalaxy/ftg_radar_native/RadarGeofenceReceiver.kt
- android/src/main/kotlin/com/foodtruckgalaxy/ftg_radar_native/RadarGeofenceRegistrar.kt
- android/src/main/kotlin/com/foodtruckgalaxy/ftg_radar_native/RadarDeliveryWorker.kt
- android/src/main/kotlin/com/foodtruckgalaxy/ftg_radar_native/RadarWorkerScheduler.kt
- android/build.gradle
- pubspec.yaml

Add:
- android/src/main/kotlin/com/foodtruckgalaxy/ftg_radar_native/RadarLocalNotifier.kt
- android/src/main/kotlin/com/foodtruckgalaxy/ftg_radar_native/RadarLocalPresenceStore.kt
- android/src/test/kotlin/com/foodtruckgalaxy/ftg_radar_native/RadarGeofenceModelsLocalNotificationTest.kt
- android/src/test/kotlin/com/foodtruckgalaxy/ftg_radar_native/RadarLocalPresenceStoreTest.kt
- android/src/test/kotlin/com/foodtruckgalaxy/ftg_radar_native/RadarWorkerSchedulerRaceTest.kt

## Intended behavior
1. Registration plan contains only open, compatible, non-favorite trucks.
2. Registration seeds local inside/outside state so `INITIAL_TRIGGER_ENTER` does not generate a false alert.
3. A real outside -> inside transition posts the Android notification locally.
4. The queued event records whether that local alert was successfully shown.
5. Backend always records the movement/inbox notification.
6. If local alert was shown, no duplicate OneSignal push is queued.
7. If local alert could not be shown, OneSignal remains the fallback.
8. Radar remote pushes have TTL=0 so stale alerts cannot appear kilometers later.
9. WorkManager uses APPEND_OR_REPLACE + expedited fallback to close the prior KEEP race.

## Validation completed here
- Kotlin production-source syntax/type smoke compile against Android/Work/GMS signature stubs: PASS.
- Pure local-presence/model smoke test: PASS (`SMOKE_OK`).
- Backend regression: favorite real CLOSED -> OPEN creates 1 notification + 1 push job in rollback test: PASS.
- Backend regression: normal favorite => 1 push job; Radar fallback => 1; Radar local-primary => 0 duplicate push: PASS.
- Geofence plan regression: simulated closed truck was registered before fix, and is excluded after fix: PASS.
- Current plan: every truck has a name and every registered truck is currently open: PASS.

## Not yet validated
A real Android Gradle/Flutter build and physical-device test cannot be claimed until the GitHub repository accepts writes and CI/APK is rebuilt. The connected GitHub installation currently returns 403 `Resource not accessible by integration` on branch creation.
