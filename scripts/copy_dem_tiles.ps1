#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Copie les tuiles DEM SRTM depuis le disque D: vers le projet GeoSylva-new.
    
.DESCRIPTION
    Ce script copie les tuiles d'élévation HGT depuis le snapshot disque D:\projet\GeoSylva\dem_pack
    vers le module dem_pack du projet actuel. Les tuiles ne sont pas versionnées dans Git
    (taille ~1.4 GB) mais ce script permet de les synchroniser facilement.
    
.PARAMETER Source
    Chemin source des tuiles sur le disque D: (défaut: D:\projet\GeoSylva\dem_pack\src\main\assets\dem_tiles)
    
.PARAMETER Dest
    Chemin destination dans le projet (défaut: .\dem_pack\src\main\assets\dem_tiles)
    
.EXAMPLE
    .\scripts\copy_dem_tiles.ps1
    
.EXAMPLE
    .\scripts\copy_dem_tiles.ps1 -WhatIf
#>

param(
    [string]$Source = "D:\projet\GeoSylva\dem_pack\src\main\assets\dem_tiles",
    [string]$Dest = "$PSScriptRoot\..\dem_pack\src\main\assets\dem_tiles",
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"

# Vérifier source
if (-not (Test-Path $Source)) {
    Write-Error "Source introuvable: $Source"
    Write-Host "Vérifiez que le disque D: est branché et contient le projet GeoSylva."
    exit 1
}

# Créer destination si nécessaire
if (-not (Test-Path $Dest)) {
    New-Item -ItemType Directory -Force -Path $Dest | Out-Null
    Write-Host "Répertoire créé: $Dest"
}

# Compter tuiles source
$srcTiles = Get-ChildItem -Path $Source -Filter "*.hgt" -File
$srcCount = $srcTiles.Count
$srcSizeGB = ($srcTiles | Measure-Object -Sum Length).Sum / 1GB

Write-Host "=== Copie des tuiles DEM SRTM ==="
Write-Host "Source: $Source"
Write-Host "Destination: $Dest"
Write-Host "Tuiles trouvées: $srcCount (~$([math]::Round($srcSizeGB, 2)) GB)"
Write-Host ""

if ($srcCount -eq 0) {
    Write-Warning "Aucune tuile .hgt trouvée à la source."
    exit 0
}

# Copier avec Robocopy (rapide, résume les erreurs)
$robocopyArgs = @(
    "$Source",
    "$Dest",
    "*.hgt",
    "/NP",     # No Progress (pas de pourcentage, juste fichiers)
    "/NJH",    # No Job Header
    "/NJS",    # No Job Summary
    "/R:3",    # 3 retries
    "/W:5"     # 5 sec wait between retries
)

if ($WhatIf) {
    Write-Host "[SIMULATION] Commande qui serait exécutée:"
    Write-Host "robocopy $robocopyArgs"
    exit 0
}

Write-Host "Copie en cours..."
$robocopyOutput = & robocopy @robocopyArgs 2>&1
$exitCode = $LASTEXITCODE

# Robocopy exit codes: 0-7 = success, 8+ = error
if ($exitCode -ge 8) {
    Write-Error "Robocopy a échoué (code $exitCode)"
    $robocopyOutput | ForEach-Object { Write-Host $_ }
    exit 1
}

# Vérifier destination
$destTiles = Get-ChildItem -Path $Dest -Filter "*.hgt" -File
$destCount = $destTiles.Count
$destSizeGB = ($destTiles | Measure-Object -Sum Length).Sum / 1GB

Write-Host ""
Write-Host "=== Copie terminée ==="
Write-Host "Tuiles copiées: $destCount / $srcCount"
Write-Host "Taille totale: $([math]::Round($destSizeGB, 2)) GB"

if ($destCount -eq $srcCount) {
    Write-Host "✅ Toutes les tuiles ont été copiées avec succès."
} else {
    Write-Warning "⚠️  $($srcCount - $destCount) tuiles manquantes."
}

Write-Host ""
Write-Host "Prochaine étape: Build du projet avec './gradlew :dem_pack:assemble'"
