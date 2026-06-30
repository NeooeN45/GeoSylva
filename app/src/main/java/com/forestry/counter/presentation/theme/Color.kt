package com.forestry.counter.presentation.theme

import androidx.compose.ui.graphics.Color

// Light Theme Colors (Futuristic green focus)
val Primary = Color(0xFF00E676)          // Vivid green
val PrimaryVariant = Color(0xFF00C853)    // Deeper green
val Secondary = Color(0xFF64FFDA)         // Aqua accent
val SecondaryVariant = Color(0xFF1DE9B6)  // Mint accent

val Background = Color(0xFFFFFFFF)        // Clean white
val Surface = Color(0xFFF3F6F5)           // Soft near-white surface
val Error = Color(0xFFB00020)

val OnPrimary = Color(0xFF000000)         // Black text on vivid green
val OnSecondary = Color(0xFF000000)
val OnBackground = Color(0xFF0A0D0C)      // Near-black text
val OnSurface = Color(0xFF0F1412)
val OnError = Color(0xFFFFFFFF)

// Dark Theme Colors (Neon on near-black)
val PrimaryDark = Color(0xFF00FF88)       // Neon green
val PrimaryVariantDark = Color(0xFF00E676)
val SecondaryDark = Color(0xFF64FFDA)
val SecondaryVariantDark = Color(0xFF1DE9B6)

val BackgroundDark = Color(0xFF0B0F0A)    // Deep near-black
val SurfaceDark = Color(0xFF101412)       // Slightly raised surface
val ErrorDark = Color(0xFFCF6679)

val OnPrimaryDark = Color(0xFF000000)
val OnSecondaryDark = Color(0xFF000000)
val OnBackgroundDark = Color(0xFFFFFFFF)
val OnSurfaceDark = Color(0xFFFFFFFF)
val OnErrorDark = Color(0xFF000000)

// Accent Color Options
val AccentGreen = Color(0xFF4CAF50)
val AccentBlue = Color(0xFF2196F3)
val AccentTeal = Color(0xFF009688)
val AccentOrange = Color(0xFFFF9800)
val AccentPurple = Color(0xFF9C27B0)
val AccentRed = Color(0xFFF44336)

// Neutral Colors
val Gray50 = Color(0xFFFAFAFA)
val Gray100 = Color(0xFFF5F5F5)
val Gray200 = Color(0xFFEEEEEE)
val Gray300 = Color(0xFFE0E0E0)
val Gray400 = Color(0xFFBDBDBD)
val Gray500 = Color(0xFF9E9E9E)
val Gray600 = Color(0xFF757575)
val Gray700 = Color(0xFF616161)
val Gray800 = Color(0xFF424242)
val Gray900 = Color(0xFF212121)

// ── Couleurs sémantiques (remplacent les Color(0xFF...) hardcoded) ───────────
// Utiliser ces constantes au lieu de Color(0xFF...) dans les écrans

// Statuts / niveaux
val SemanticSuccess = Color(0xFF2E7D32)     // Vert succès (IBP bon, martelage Avenir)
val SemanticWarning = Color(0xFFF57C00)     // Orange avertissement
val SemanticError = Color(0xFFC62828)       // Rouge erreur (IBP faible, Dépérir)
val SemanticInfo = Color(0xFF1565C0)        // Bleu information (diagnostic station)

// Catégories de martelage
val MartelageAvenir = Color(0xFF2E7D32)     // Vert
val MartelageReserve = Color(0xFF1565C0)    // Bleu
val MartelageEnlever = Color(0xFFE65100)    // Orange
val MartelageDeperir = Color(0xFFC62828)    // Rouge
val MartelageBiodiv = Color(0xFF7B1FA2)     // Violet

// Essences (codes couleur courants)
val EssenceFeuillu = Color(0xFF4CAF50)      // Vert feuillu
val EssenceResineux = Color(0xFF2196F3)     // Bleu résineux
val EssenceMixte = Color(0xFF795548)        // Brun mixte

// IBP (niveaux de potentiel)
val IbpTresFaible = Color(0xFFC62828)       // 0-9
val IbpFaible = Color(0xFFE65100)           // 10-19
val IbpMoyen = Color(0xFFF9A825)            // 20-29
val IbpBon = Color(0xFF2E7D32)              // 30-39
val IbpTresBon = Color(0xFF1B5E20)          // 40-50

// GPS (précision)
val GpsExcellent = Color(0xFF2E7D32)        // ≤3m
val GpsBon = Color(0xFFF9A825)              // ≤6m
val GpsModere = Color(0xFFE65100)           // ≤12m
val GpsMauvais = Color(0xFFC62828)          // >12m
