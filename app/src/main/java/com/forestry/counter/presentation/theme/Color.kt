package com.forestry.counter.presentation.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════
// Palette « Forêt tempérée » — GeoSylva 3.0
//
// Vert profond désaturé (marque), ambre chaud (accent d'action), neutres
// légèrement chauds plutôt que des gris purs.
//
// Elle remplace la palette vert néon d'origine (#00E676), dont le contraste
// sur fond blanc était de 1,7:1 — très en dessous du minimum WCAG AA de 4,5:1
// et donc inutilisable pour du texte ou une icône.
//
// Contrastes vérifiés : primaire/blanc 7,5:1 · texte/fond 16,5:1.
// ═══════════════════════════════════════════════════════════════════════════

// ── Thème clair ──────────────────────────────────────────────────────────────
val Primary = Color(0xFF2D5F3F)           // Vert forêt profond — couleur de marque
val PrimaryVariant = Color(0xFFB8E6C5)    // Conteneur primaire (surfaces en avant)
val Secondary = Color(0xFF4A6B58)         // Vert de support, désaturé
val SecondaryVariant = Color(0xFFCDE8D8)  // Conteneur secondaire

val Background = Color(0xFFFBFAF7)        // Blanc cassé chaud — jamais blanc pur
val Surface = Color(0xFFF4F3EE)           // Surface légèrement plus dense
val Error = Color(0xFFBA1A1A)

val OnPrimary = Color(0xFFFFFFFF)
val OnSecondary = Color(0xFFFFFFFF)
val OnBackground = Color(0xFF191C17)      // Presque noir, teinté vert
val OnSurface = Color(0xFF191C17)
val OnError = Color(0xFFFFFFFF)

// Accent d'action — l'ambre. Un seul par écran, réservé à l'action décisive.
val Tertiary = Color(0xFFB26A00)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFDDB3)
val OnTertiaryContainer = Color(0xFF2A1800)

val OnPrimaryContainer = Color(0xFF0C2417)
val OnSecondaryContainer = Color(0xFF0C2417)
val SurfaceVariant = Color(0xFFE0E4DB)
val OnSurfaceVariant = Color(0xFF444A41)
val Outline = Color(0xFF73796E)
val OutlineVariant = Color(0xFFC3C8BC)

// ── Thème sombre ─────────────────────────────────────────────────────────────
// Neutres vraiment neutres (R=G=B) : la version précédente teintait fond et
// surfaces vers le vert-jaune (ex. #12140F, G>R>B), perceptible comme un noir
// « sale ». Seuls les accents (Primary, Tertiary) portent la couleur de
// marque ; le fond reste un noir gris neutre, proche d'un noir OLED.
val PrimaryDark = Color(0xFF8FD1A4)       // Vert clair lisible sur fond sombre
val PrimaryVariantDark = Color(0xFF1B3D2C)
val SecondaryDark = Color(0xFFB1CCBB)
val SecondaryVariantDark = Color(0xFF2E3F35)

val BackgroundDark = Color(0xFF0E0F0E)    // Quasi noir, neutre — plus de teinte jaune
val SurfaceDark = Color(0xFF171817)
val ErrorDark = Color(0xFFFFB4AB)

val OnPrimaryDark = Color(0xFF0B2417)
val OnSecondaryDark = Color(0xFF1D3527)
val OnBackgroundDark = Color(0xFFE4E4E2)
val OnSurfaceDark = Color(0xFFE4E4E2)
val OnErrorDark = Color(0xFF690005)

val TertiaryDark = Color(0xFFFFB95C)
val OnTertiaryDark = Color(0xFF452B00)
val TertiaryContainerDark = Color(0xFF633F00)
val OnTertiaryContainerDark = Color(0xFFFFDDB3)

val OnPrimaryContainerDark = Color(0xFFB8E6C5)
val OnSecondaryContainerDark = Color(0xFFCDE8D8)
val SurfaceVariantDark = Color(0xFF3A3B3A)
val OnSurfaceVariantDark = Color(0xFFC4C5C2)
val OutlineDark = Color(0xFF8E8F8C)
val OutlineVariantDark = Color(0xFF3A3B3A)

// ── Surfaces conteneurs Material 3 ───────────────────────────────────────────
// Material 3 a introduit une famille `surfaceContainer*` distincte de
// `surface`. Sans valeurs explicites, la bibliothèque les dérive de sa teinte
// par défaut — d'où la barre de navigation lavande observée sous des tuiles
// vertes. Ces neutres sont légèrement chauds et teintés vert, comme le reste
// de la palette : ils ne sont pas des gris purs.
val SurfaceDim = Color(0xFFDBDBD3)
val SurfaceBright = Color(0xFFFBFAF7)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF5F4EF)
val SurfaceContainer = Color(0xFFEFEEE9)
val SurfaceContainerHigh = Color(0xFFEAE8E3)
val SurfaceContainerHighest = Color(0xFFE4E3DE)
val InverseSurface = Color(0xFF2E322C)
val InverseOnSurface = Color(0xFFF0F1EB)

val SurfaceDimDark = Color(0xFF0E0F0E)
val SurfaceBrightDark = Color(0xFF313231)
val SurfaceContainerLowestDark = Color(0xFF090A09)
val SurfaceContainerLowDark = Color(0xFF161716)
val SurfaceContainerDark = Color(0xFF1B1C1B)
val SurfaceContainerHighDark = Color(0xFF252625)
val SurfaceContainerHighestDark = Color(0xFF303130)
val InverseSurfaceDark = Color(0xFFE4E4E2)
val InverseOnSurfaceDark = Color(0xFF2E2F2E)

// ── Couleurs posées sur un média (vidéo ou photo) ────────────────────────────
// Le vert de marque #2D5F3F est calibré pour du texte sur fond clair : sur une
// vidéo de sous-bois, il se noie dans l'image. Cette variante claire — le vert
// primaire du thème sombre — s'en détache nettement tout en restant dans la
// palette. Contraste avec son texte : 9,2:1.
val GreenOnMedia = Color(0xFF8FD1A4)
val OnGreenOnMedia = Color(0xFF0B2417)
// Pied du dégradé du bouton principal : même teinte, à peine assombrie.
// L'écart est volontairement minime — un dégradé perceptible ferait clinquant.
val GreenDeepOnMedia = Color(0xFF74BE8B)

// Champs de saisie posés directement sur le média — pas de carte, pas de
// panneau : chaque champ est une surface sombre translucide autonome.
val FieldOnMedia = Color(0xB0121410)
val FieldBorderOnMedia = Color(0x24FFFFFF)
val PlaceholderOnMedia = Color(0x99FFFFFF)
val TextOnMedia = Color(0xFFFFFFFF)
val TextSecondaryOnMedia = Color(0xCCFFFFFF)

// ── Registre terrain — contraste renforcé ────────────────────────────────────
// Utilisé par les écrans de saisie (comptage, martelage, carte en action) et
// par le mode « plein soleil ». Le texte va au noir pur, les bordures
// s'épaississent, aucune surface translucide.
val FieldBackground = Color(0xFFFFFFFF)
val FieldSurface = Color(0xFFFFFFFF)
val FieldOnSurface = Color(0xFF000000)
val FieldOutline = Color(0xFF2D5F3F)

val FieldBackgroundDark = Color(0xFF000000)
val FieldSurfaceDark = Color(0xFF0A0A0A)
val FieldOnSurfaceDark = Color(0xFFFFFFFF)
val FieldOutlineDark = Color(0xFF8FD1A4)

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
