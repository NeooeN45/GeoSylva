# Tuiles DEM SRTM pour GeoSylva

Ce dossier contient les tuiles MNT (Modèle Numérique de Terrain) SRTM au format HGT.

## Format attendu

- **Format** : HGT (NASA SRTM)
- **Résolution** : 3 arc-seconds (~90m) ou 1 arc-second (~30m)
- **Dimensions** : 1201×1201 points (3") ou 3601×3601 points (1")
- **Encodage** : Int16 big-endian, metres
- **Nodata** : -32768

## Nommage des fichiers

```
N{lat:02d}E{lon:03d}.hgt   (ex: N46E005.hgt)
N{lat:02d}W{lon:03d}.hgt   (ex: N47W002.hgt)
S{lat:02d}E{lon:03d}.hgt   (ex: S01E012.hgt)
S{lat:02d}W{lon:03d}.hgt   (ex: S05W080.hgt)
```

## Source des données

### Option 1 : CGIAR-CSI SRTM v4.1 (90m) - RECOMMANDÉ
- URL : https://srtm.csi.cgiar.org/srtmdata/
- Format : TIFF géoréférencé (nécessite conversion)
- Licence : CC-BY
- Couverture : mondiale

**Conversion TIFF → HGT :**
```bash
gdal_translate -of SRTMHGT srtm_36_04.tif N46E005.hgt
```

### Option 2 : NASA Earthdata (30m)
- URL : https://earthexplorer.usgs.gov/
- Format : HGT natif
- Résolution : 1 arc-second (~30m)
- Nécessite compte gratuit NASA

### Option 3 : Viewfinder Panoramas
- URL : http://viewfinderpanoramas.org/Coverage%20map%20viewfinderpanoramas_org3.htm
- Format : HGT
- Couverture : Europe complète

## Tuiles nécessaires pour la France

### France métropolitaine
```
Lat: 41°N à 51°N
Lon: 6°W à 10°E
```

**Liste complète (~160 tuiles) :**
```
N41W006.hgt N41W005.hgt N41W004.hgt ... N41E010.hgt
N42W006.hgt N42W005.hgt ...           ... N42E010.hgt
...
N51W006.hgt ...                       ... N51E010.hgt
```

### Corse
```
N41E008.hgt N41E009.hgt N41E010.hgt
N42E008.hgt N42E009.hgt N42E010.hgt
N43E008.hgt N43E009.hgt N43E010.hgt
```

## Taille estimée

- **Version 90m (3")** : ~160 tuiles × ~600 KB = **~96 MB**
- **Version 30m (1")** : ~160 tuiles × ~2.5 MB = **~400 MB**

## Script de téléchargement

Utilisez le script Python fourni :
```bash
cd ../../../../../scripts
python download_srtm_tiles.py --output ../dem_pack/src/main/assets/dem_tiles
```

## Vérification

Après ajout des tuiles, vérifiez la configuration :
```bash
# Nombre de tuiles présentes
ls -1 *.hgt | wc -l

# Taille totale
du -sh .
```

## Intégration Play Asset Delivery

Les tuiles sont packagées automatiquement via le module `:dem_pack` :
- `install-time` : téléchargé lors de l'installation de l'app
- Taille max : 150 MB pour install-time

## Fallback

Si une tuile est manquante, l'app utilise :
1. `EmbeddedDemService` → tuile locale HGT
2. `SrtmElevationService` → API OpenTopography (online)
3. `TerritorialResolver.interpolateAltitude()` → interpolation IDW (52 points réf.)

## Notes

- Les tuiles doivent être **strictement au format HGT NASA**
- Pas de compression (pas de .zip, .gz, etc. dans ce dossier)
- Vérifier les permissions de lecture après copie
