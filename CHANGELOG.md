# Changelog

## 1.4.0

- Surveillance Android par `GeofencingClient`, indépendante du processus Flutter.
- 99 zones trucks au maximum plus une sentinelle de déplacement.
- Événements persistés avant livraison et retries réseau via WorkManager.
- Réenregistrement après redémarrage ou mise à jour de l'application.
- Le service foreground historique reste disponible pour comparaison mais n'est plus démarré.

## 1.3.0

- RÃ©veil Android durable sur dÃ©placement via un `PendingIntent` systÃ¨me.
- RÃ©ception et synchronisation natives mÃªme lorsque le processus Flutter a Ã©tÃ© retirÃ© des applications rÃ©centes.
- Tests Android simulant l'enregistrement GPS et rÃ©seau ainsi que l'arrÃªt explicite du Radar.

## 1.2.0

- Implémentation native iOS avec Core Location et suivi en arrière-plan.
- Stockage du token Radar dans le trousseau iOS.
- Même API et même diagnostic non sensible sur Android et iOS.
- Exemple iOS configuré et CI macOS avec build simulateur réel.

## 1.1.0

- Validation stricte du token et de l'endpoint HTTPS côté Dart et Kotlin.
- Service Android 14+ déclaré et démarré avec le type `location` requis.
- État d'exécution réel et diagnostic de la dernière synchronisation HTTP.
- Redémarrage `START_STICKY` avec configuration persistée.
- Arrêt immédiat et révocation locale sur réponse HTTP 401/403.
- Tests Dart, Flutter et Kotlin, application d'exemple et CI Android complète.

## 1.0.0

- Première version Android du service Radar FTG.
