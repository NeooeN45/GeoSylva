package com.forestry.counter.presentation.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════
// Direction artistique « Canopée » — GeoSylva 3.1
//
// Carnet de forestier : papier crème (clair) / sous-bois nocturne vert-bleu
// (sombre), épicéa profond pour la marque, mousse claire en sombre, ambre
// « sève » réservé à l'action décisive. Les contenus sont posés sur des
// cartes légèrement plus claires que le papier, jamais sur des gris purs.
//
// Elle remplace la palette vert néon d'origine (#00E676), dont le contraste
// sur fond blanc était de 1,7:1 — très en dessous du minimum WCAG AA de 4,5:1
// et donc inutilisable pour du texte ou une icône.
//
// Contrastes vérifiés (WCAG) : primaire/papier 9,0:1 · texte/papier 15,0:1 ·
// mousse/sous-bois 11,6:1 · texte/sous-bois 15,5:1.
// ═══════════════════════════════════════════════════════════════════════════

// ── Thème clair ──────────────────────────────────────────────────────────────
val Primary = Color(0xFF1B4A38)           // Épicéa — vert-bleu profond, couleur de marque
val PrimaryVariant = Color(0xFFCFE6C9)    // Conteneur primaire (surfaces en avant)
val Secondary = Color(0xFF506559)         // Vert de support, désaturé
val SecondaryVariant = Color(0xFFE2E9D6)  // Conteneur secondaire

val Background = Color(0xFFF5F2E8)        // Papier de carnet — jamais blanc pur
val Surface = Color(0xFFFBF9F2)           // Carte posée sur le papier
val Error = Color(0xFFBA1A1A)

val OnPrimary = Color(0xFFFFFFFF)
val OnSecondary = Color(0xFFFFFFFF)
val OnBackground = Color(0xFF15201A)      // Presque noir, teinté vert
val OnSurface = Color(0xFF15201A)
val OnError = Color(0xFFFFFFFF)

// Accent d'action — l'ambre. Un seul par écran, réservé à l'action décisive.
val Tertiary = Color(0xFF8F5200)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFDCAE)
val OnTertiaryContainer = Color(0xFF2A1800)

val OnPrimaryContainer = Color(0xFF0B2A1D)
val OnSecondaryContainer = Color(0xFF14261C)
val SurfaceVariant = Color(0xFFE3E6D8)
val OnSurfaceVariant = Color(0xFF4A5249)
val Outline = Color(0xFF636C60)
val OutlineVariant = Color(0xFFCDD0C0)

// ── Thème sombre ─────────────────────────────────────────────────────────────
// Neutres vraiment neutres (R=G=B) : la version précédente teintait fond et
// surfaces vers le vert-jaune (ex. #12140F, G>R>B), perceptible comme un noir
// « sale ». Seuls les accents (Primary, Tertiary) portent la couleur de
// marque ; le fond reste un noir gris neutre, proche d'un noir OLED.
val PrimaryDark = Color(0xFF94DBAB)       // Mousse claire, lisible sur fond sombre
val PrimaryVariantDark = Color(0xFF1D4231)
val SecondaryDark = Color(0xFFB6CDBC)
val SecondaryVariantDark = Color(0xFF273A2F)

val BackgroundDark = Color(0xFF0B1410)    // Sous-bois nocturne — noir vert-bleu, sans teinte jaune
val SurfaceDark = Color(0xFF111C17)
val ErrorDark = Color(0xFFFFB4AB)

val OnPrimaryDark = Color(0xFF06261A)
val OnSecondaryDark = Color(0xFF1D3527)
val OnBackgroundDark = Color(0xFFE6EBE4)
val OnSurfaceDark = Color(0xFFE6EBE4)
val OnErrorDark = Color(0xFF690005)

val TertiaryDark = Color(0xFFFFBE66)
val OnTertiaryDark = Color(0xFF452B00)
val TertiaryContainerDark = Color(0xFF633F00)
val OnTertiaryContainerDark = Color(0xFFFFDDB3)

val OnPrimaryContainerDark = Color(0xFFCFE6C9)
val OnSecondaryContainerDark = Color(0xFFCDE8D8)
val SurfaceVariantDark = Color(0xFF2B3A32)
val OnSurfaceVariantDark = Color(0xFFB4BDB2)
val OutlineDark = Color(0xFF86907F)
val OutlineVariantDark = Color(0xFF2B3A32)

// ── Surfaces conteneurs Material 3 ───────────────────────────────────────────
// Material 3 a introduit une famille `surfaceContainer*` distincte de
// `surface`. Sans valeurs explicites, la bibliothèque les dérive de sa teinte
// par défaut — d'où la barre de navigation lavande observée sous des tuiles
// vertes. Ces neutres sont légèrement chauds et teintés vert, comme le reste
// de la palette : ils ne sont pas des gris purs.
val SurfaceDim = Color(0xFFDAD8CB)
val SurfaceBright = Color(0xFFFBF9F2)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF8F6EE)
val SurfaceContainer = Color(0xFFF0EEE3)
val SurfaceContainerHigh = Color(0xFFEAE8DC)
val SurfaceContainerHighest = Color(0xFFE4E2D5)
val InverseSurface = Color(0xFF223028)
val InverseOnSurface = Color(0xFFF0F1EB)

val SurfaceDimDark = Color(0xFF0B1410)
val SurfaceBrightDark = Color(0xFF2A3730)
val SurfaceContainerLowestDark = Color(0xFF070D0A)
val SurfaceContainerLowDark = Color(0xFF0F1A15)
val SurfaceContainerDark = Color(0xFF14211B)
val SurfaceContainerHighDark = Color(0xFF1B2A23)
val SurfaceContainerHighestDark = Color(0xFF24342C)
val InverseSurfaceDark = Color(0xFFE6EBE4)
val InverseOnSurfaceDark = Color(0xFF223028)

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
val FieldOutline = Color(0xFF1B4A38)

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
