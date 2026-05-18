# DEM Tiles - Tuiles d'Élevation SRTM

Ce répertoire contient les tuiles d'élévation SRTM (Shuttle Radar Topography Mission) 
au format HGT (NASA/CGIAR 3 arc-second ≈ 90m).

## Source des données
- **Origine** : CGIAR-CSI SRTM v4.1
- **Licence** : CC-BY (Creative Commons Attribution)
- **URL** : https://srtm.csi.cgiar.org/srtmdata/

## Format des tuiles
- **Nommage** : `N{lat:02d}E{lon:03d}.hgt` (ex: N45E001.hgt)
- **Résolution** : 1201 × 1201 points (3 arc-seconds)
- **Taille** : ~2.8 MB par tuile (1° × 1°)
- **Encodage** : Int16 big-endian, -32768 = nodata

## Couverture
Les tuiles couvrent la France métropolitaine et zones limitrophes :
- Latitudes : N40° à N54° (15 bandes)
- Longitudes : E000° à E024° (25 colonnes)
- **Total** : ~375 tuiles = ~1 GB

## Installation des tuiles

### Option 1 : Copie manuelle depuis le disque D:
```powershell
.\scripts\copy_dem_tiles.ps1
```

### Option 2 : Play Asset Delivery (automatique)
Les tuiles sont intégrées à l'APK via Android Asset Pack (`dem_pack` module)
et téléchargées à l'installation si configuré.

### Option 3 : Téléchargement in-app
Les tuiles peuvent être téléchargées à la demande et stockées dans :
- `context.filesDir/dem/` (interne)
- `context.getExternalFilesDir(null)/dem/` (externe/SD)

## Service utilisateur
Voir : `EmbeddedDemService.kt` pour la lecture des tuiles et calcul pente/exposition.
