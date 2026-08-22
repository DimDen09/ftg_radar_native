# Changelog

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
