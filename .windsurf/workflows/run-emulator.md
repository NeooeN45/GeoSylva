---
description: Lancer l'émulateur Android et déployer GeoSylva en debug
---

## Étapes

### 1. Démarrer l'émulateur (Pixel 9 Pro XL — API 36)
// turbo
```pwsh
Start-Process -FilePath "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -ArgumentList "-avd Pixel_9_Pro_XL -no-snapshot-load" -WindowStyle Normal
```
> Attendre ~30s que l'émulateur boot complètement avant de continuer.

### 2. Vérifier que l'émulateur est prêt (boot animation terminée)
// turbo
```pwsh
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" wait-for-device; & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell getprop sys.boot_completed
```
> La commande retourne `1` quand le device est prêt.

### 3. Compiler et installer l'APK debug
```pwsh
.\gradlew :app:installDebug
```

### 4. Lancer l'app directement
// turbo
```pwsh
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.forestry.counter/.presentation.MainActivity
```

### 5. Voir les logs en temps réel (filtré GeoSylva)
// turbo
```pwsh
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s "GeoSylva:*" "AndroidRuntime:E" "System.err:W" --pid=$(& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell pidof com.forestry.counter)
```
