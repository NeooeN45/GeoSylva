# Helper pour interagir avec l'emulateur Android via ADB
# Usage: .\test_helper.ps1 <action> [args]

param(
    [Parameter(Mandatory=$true)]
    [string]$Action,
    [string]$Text,
    [int]$X,
    [int]$Y,
    [string]$InputText
)

$adb = "C:\Users\camil\AppData\Local\Android\Sdk\platform-tools\adb.exe"

function DumpUI {
    & $adb shell uiautomator dump /sdcard/ui_dump.xml 2>$null
    return & $adb shell cat /sdcard/ui_dump.xml 2>$null
}

function GetTexts {
    param([string]$Xml)
    return $Xml | Select-String 'text="[^"]+"' -AllMatches | ForEach-Object { $_.Matches } | ForEach-Object { $_.Value }
}

function TapByText {
    param([string]$SearchText)
    $xml = DumpUI
    $lines = $xml -split ">"
    foreach ($line in $lines) {
        if ($line -match "text=`"$SearchText`"" -and $line -match 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
            $x1=[int]$Matches[1]; $y1=[int]$Matches[2]; $x2=[int]$Matches[3]; $y2=[int]$Matches[4]
            $cx=[int](($x1+$x2)/2); $cy=[int](($y1+$y2)/2)
            Write-Output "TAP: '$SearchText' at ($cx, $cy)"
            & $adb shell input tap $cx $cy 2>$null
            return $true
        }
    }
    Write-Output "NOT FOUND: '$SearchText'"
    return $false
}

function ShowUI {
    $xml = DumpUI
    $texts = GetTexts -Xml $xml
    Write-Output "=== UI Elements ==="
    $texts | ForEach-Object { Write-Output $_ }
}

switch ($Action) {
    "tap" {
        TapByText -SearchText $Text
        Start-Sleep -Seconds 2
    }
    "show" {
        ShowUI
    }
    "tapshow" {
        TapByText -SearchText $Text
        Start-Sleep -Seconds 2
        ShowUI
    }
    "input" {
        & $adb shell input text $InputText 2>$null
        Start-Sleep -Seconds 1
    }
    "tapxy" {
        & $adb shell input tap $X $Y 2>$null
        Start-Sleep -Seconds 2
    }
    "swipe" {
        & $adb shell input swipe 1000 1500 100 1500 300 2>$null
        Start-Sleep -Seconds 1
    }
    "back" {
        & $adb shell input keyevent 4 2>$null
        Start-Sleep -Seconds 2
    }
    "screenshot" {
        & $adb exec-out screencap -p > "C:\Users\camil\Desktop\Micro Entreprise\04_PROJETS_EN_COURS\Projet\GeoSylva-new\test_screenshot.png" 2>$null
        Write-Output "Screenshot saved"
    }
    "crashes" {
        $crashes = & $adb logcat -d 2>$null | Select-String "FATAL EXCEPTION|E AndroidRuntime" -Context 0,3
        if ($crashes) {
            Write-Output "=== CRASHES DETECTES ==="
            $crashes | Select-Object -Last 10
        } else {
            Write-Output "Aucun crash detecte"
        }
    }
    default {
        Write-Output "Actions: tap, show, tapshow, input, tapxy, swipe, back, screenshot, crashes"
    }
}
