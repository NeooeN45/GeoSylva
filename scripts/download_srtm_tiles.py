#!/usr/bin/env python3
"""
Script de téléchargement des tuiles SRTM HGT pour GeoSylva
Source: CGIAR-CSI SRTM v4.1 (90m resolution)
Zone: France métropolitaine (N41-N51, W06-E10)
Format: HGT 1201x1201, 16-bit signed big-endian
"""

import os
import sys
import requests
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

# Configuration
BASE_URL = "https://srtm.csi.cgiar.org/wp-content/uploads/files/srtm_5x5/TIFF"
FRANCE_BOUNDS = {
    "lat_min": 41,   # Sud
    "lat_max": 51,   # Nord
    "lon_min": -6,   # Ouest
    "lon_max": 10    # Est
}

# Tuiles spécifiques pour France (optimisé)
FRANCE_TILES = [
    # Littoral atlantique + Manche
    ("srtm_35_04", 41, -6), ("srtm_36_04", 41, -1),
    ("srtm_35_05", 46, -6), ("srtm_36_05", 46, -1),
    # Centre
    ("srtm_36_03", 41, 4), ("srtm_37_03", 41, 9),
    ("srtm_36_04", 46, 4), ("srtm_37_04", 46, 9),
    # Est + Alpes
    ("srtm_37_02", 36, 9), ("srtm_38_02", 36, 14),
    ("srtm_37_03", 41, 9), ("srtm_38_03", 41, 14),
    # Méditerranée
    ("srtm_37_01", 31, 9), ("srtm_38_01", 31, 14),
    ("srtm_37_02", 36, 9), ("srtm_38_02", 36, 14),
    # Corse
    ("srtm_39_03", 41, 19),
]


def get_tile_filename(lat: int, lon: int) -> str:
    """Génère le nom de fichier HGT standard NASA."""
    ns = "N" if lat >= 0 else "S"
    ew = "E" if lon >= 0 else "W"
    return f"{ns}{abs(lat):02d}{ew}{abs(lon):03d}.hgt"


def get_cgiar_url(lat: int, lon: int) -> str:
    """Génère l'URL CGIAR-CSI pour une tuile."""
    # Calcul du numéro de tuile CGIAR (grille 5x5 degrés)
    # Les tuiles CGIAR couvrent 5°x5°, nommées srtm_XX_YY
    tile_x = (lon + 180) // 5 + 1  # Colonne
    tile_y = (60 - lat) // 5 + 1   # Ligne (origine au nord)
    return f"{BASE_URL}/srtm_{tile_x:02d}_{tile_y:02d}.zip"


def download_tile(url: str, output_dir: Path, timeout: int = 60) -> bool:
    """Télécharge une tuile ZIP et l'extrait."""
    try:
        filename = url.split("/")[-1]
        zip_path = output_dir / filename
        
        # Skip si déjà présent
        if zip_path.exists():
            print(f"  ✓ {filename} déjà présent")
            return True
        
        print(f"  ↓ Téléchargement {filename}...")
        response = requests.get(url, timeout=timeout, stream=True)
        response.raise_for_status()
        
        with open(zip_path, "wb") as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
        
        print(f"  ✓ {filename} téléchargé ({zip_path.stat().st_size / 1024 / 1024:.1f} MB)")
        return True
        
    except requests.exceptions.RequestException as e:
        print(f"  ✗ Erreur {filename}: {e}")
        return False


def generate_france_tile_list() -> list:
    """Génère la liste complète des tuiles couvrant la France."""
    tiles = []
    
    # France métropolitaine: N41 à N51, W06 à E10
    for lat in range(41, 52):      # N41 à N51
        for lon in range(-6, 11):  # W06 à E10
            tiles.append((lat, lon))
    
    # Corse: N41 à N43, E008 à E010
    for lat in range(41, 44):
        for lon in range(8, 11):
            if (lat, lon) not in tiles:
                tiles.append((lat, lon))
    
    return tiles


def main():
    parser = argparse.ArgumentParser(
        description="Télécharge les tuiles SRTM HGT pour GeoSylva"
    )
    parser.add_argument(
        "--output", "-o",
        default="dem_tiles",
        help="Répertoire de sortie (défaut: dem_tiles)"
    )
    parser.add_argument(
        "--threads", "-t",
        type=int,
        default=4,
        help="Nombre de threads de téléchargement (défaut: 4)"
    )
    parser.add_argument(
        "--verify", "-v",
        action="store_true",
        help="Vérifier les tuiles existantes sans télécharger"
    )
    
    args = parser.parse_args()
    
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print("=" * 60)
    print("GeoSylva — Téléchargement tuiles SRTM HGT")
    print("Source: CGIAR-CSI SRTM v4.1 (90m)")
    print("Zone: France métropolitaine + Corse")
    print("=" * 60)
    
    tiles = generate_france_tile_list()
    print(f"\nTuiles à traiter: {len(tiles)}")
    print(f"Répertoire: {output_dir.absolute()}")
    print(f"Threads: {args.threads}")
    print()
    
    if args.verify:
        # Mode vérification
        print("Mode vérification:")
        for lat, lon in tiles:
            filename = get_tile_filename(lat, lon)
            hgt_path = output_dir / filename
            status = "✓" if hgt_path.exists() else "✗"
            print(f"  {status} {filename}")
        return
    
    # Téléchargement
    print("Téléchargement des tuiles CGIAR (format TIFF/ZIP)...")
    print("Note: Les tuiles CGIAR sont au format TIFF, conversion en HGT nécessaire")
    print()
    
    # URLs CGIAR uniques
    cgiar_urls = set()
    for lat, lon in tiles:
        url = get_cgiar_url(lat, lon)
        cgiar_urls.add(url)
    
    print(f"URLs CGIAR uniques: {len(cgiar_urls)}")
    print()
    
    successful = 0
    failed = 0
    
    with ThreadPoolExecutor(max_workers=args.threads) as executor:
        future_to_url = {
            executor.submit(download_tile, url, output_dir): url 
            for url in cgiar_urls
        }
        
        for future in as_completed(future_to_url):
            if future.result():
                successful += 1
            else:
                failed += 1
    
    print()
    print("=" * 60)
    print("Résumé:")
    print(f"  Tuiles téléchargées: {successful}")
    print(f"  Échecs: {failed}")
    print()
    print("NOTE IMPORTANTE:")
    print("Les tuiles CGIAR sont au format TIFF géoréférencé.")
    print("GeoSylva attend du format HGT (NASA SRTM).")
    print("Conversion nécessaire avec gdal_translate:")
    print("  gdal_translate -of SRTMHGT input.tif output.hgt")
    print()
    print("Alternative: Utiliser les tuiles HGT directement depuis:")
    print("  https://dwtkns.com/srtm30m/srtm30m_boundaryRequest.cgi")
    print("=" * 60)


if __name__ == "__main__":
    main()
