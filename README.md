# VideoBackground (Android)

Cette application Android permet d'enregistrer une vidéo en arrière-plan :
- Appuyez sur "Prendre une vidéo" pour démarrer l'enregistrement (la caméra s'active en arrière-plan, rien n'est visible à l'écran).
- Vous pouvez utiliser le téléphone normalement pendant l'enregistrement.
- Revenez dans l'application et appuyez sur "Stop" pour arrêter et sauvegarder la vidéo.
- La vidéo est enregistrée dans le dossier `Movies/VideoBackground`.

## Structure des fichiers

- `AndroidManifest.xml` : Permissions et déclaration des composants.
- `res/layout/activity_main.xml` : Interface avec deux boutons.
- `src/main/java/com/lucbo/phone/MainActivity.kt` : Activité principale.
- `src/main/java/com/lucbo/phone/VideoRecordService.kt` : Service d'enregistrement vidéo en arrière-plan.

## Prérequis
- Android Studio
- Un téléphone Android (mode développeur et débogage USB activés)

## Installation
1. Ouvrez ce dossier dans Android Studio.
2. Branchez votre téléphone Android.
3. Cliquez sur "Run" (triangle vert).
4. Accordez toutes les permissions demandées.

## Utilisation
- Appuyez sur "Prendre une vidéo" pour démarrer l'enregistrement.
- Quittez l'application ou utilisez le téléphone normalement.
- Revenez et appuyez sur "Stop" pour sauvegarder la vidéo.

## Remarques
- Fonctionne sur Android 8.0 (API 26) ou supérieur.
- Pour une application de production, il faudra gérer plus finement les permissions et les erreurs. 