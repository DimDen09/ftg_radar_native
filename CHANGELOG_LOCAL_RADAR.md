# FTG Radar native 1.4.1 candidate

- Fix WorkManager unique-work race: APPEND_OR_REPLACE + expedited fallback.
- Batch receiver persistence and schedule delivery once per callback.
- Add truck_name to persisted geofence specs.
- Add local Android notification on true outside -> inside truck transition.
- Seed local presence at registration to suppress INITIAL_TRIGGER_ENTER false alerts.
- Reduce Android geofence notification responsiveness from 60s to 5s.
- Persist local_notification_shown in queue and send it to backend.
- Backend can then keep the inbox notification while suppressing the duplicate OneSignal push.
- Queue events remain backward compatible: missing local_notification_shown defaults to false.
