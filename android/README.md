# Flow IPTV — Application Android

Application Android native qui lit la même playlist IPTV que le site, mais via **ExoPlayer**. Pas de limites navigateur : pas de CORS, pas de coupure à 1 minute, démarrage rapide.

## Compilation automatique

À chaque `push` qui touche le dossier `android/`, GitHub Actions compile l'APK :

1. Va dans l'onglet **Actions** de ton repo GitHub.
2. Ouvre le dernier run **Build Android APK**.
3. Télécharge l'artefact **FlowIPTV-debug-apk** (en bas de la page).
4. Décompresse et installe `app-debug.apk` sur ton téléphone (active "Sources inconnues").

> ⚠️ **Important** : GitHub Actions **zippe toujours** les artefacts au téléchargement.
> Le fichier `FlowIPTV-debug-apk.zip` contient l'APK à l'intérieur — c'est normal.
> Décompresse-le avec n'importe quel gestionnaire de fichiers (sur Android : **ZArchiver**, **Files by Google**, etc.) pour récupérer `app-debug.apk`, puis installe-le.

### Téléchargement direct sans zip (recommandé)

Pour obtenir l'APK directement (sans passer par un zip), pousse un **tag git** :

```bash
git tag v1.0
git push --tags
```

Le workflow publiera alors automatiquement l'APK comme **Release GitHub** :
- Va dans l'onglet **Releases** de ton repo
- Télécharge `app-debug.apk` directement (pas de zip)

## Lancer un build manuellement

- Onglet **Actions** → **Build Android APK** → **Run workflow**.

## Compilation locale (optionnel)

```bash
cd android
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

APK généré dans `android/app/build/outputs/apk/debug/app-debug.apk`.