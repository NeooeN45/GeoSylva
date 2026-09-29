package com.forestry.counter.presentation.components

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.BatteryManager
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.forestry.counter.R
import com.forestry.counter.presentation.theme.Motion

/**
 * Fond vidéo — **écran de connexion uniquement**.
 *
 * Doctrine des deux registres : la vidéo appartient au registre consultation,
 * et l'écran de connexion en est le seul usage dans GeoSylva. C'est le seul
 * moment où l'utilisateur n'a rien d'autre à faire qu'attendre. Sur un écran
 * de saisie terrain, le décodage vidéo concurrent au GPS, à la carte et à
 * l'appareil photo coûterait de la batterie et ferait chauffer l'appareil.
 *
 * Aucune dépendance externe : `TextureView` + `MediaPlayer` de la plateforme
 * suffisent pour une boucle locale et muette, et fonctionnent sur toutes les
 * versions d'Android supportées.
 *
 * La vidéo tourne systématiquement à l'arrivée sur l'écran de connexion.
 * La seule exception est une batterie critique (≤ [LOW_BATTERY_THRESHOLD] %) :
 * en dessous de ce seuil l'image fixe est affichée à la place, pour ne pas
 * gêner le dernier usage terrain avant extinction.
 *
 * [startPositionMs] permet de choisir la scène de départ (en millisecondes).
 * Exemple : si la scène rouge-gorge commence à 8 400 ms dans le fichier vidéo,
 * passer `startPositionMs = 8_400`.
 *
 * L'image fixe reste affichée sous la vidéo et celle-ci apparaît en fondu
 * quand la première image est décodée : aucun écran noir au démarrage.
 */
@Composable
fun VideoBackdrop(
    @RawRes videoRes: Int = R.raw.geosylva_login,
    posterRes: Int = R.drawable.login_backdrop_poster,
    /** Position de départ en ms — 0 = début du fichier. */
    startPositionMs: Int = 0,
    animationsEnabled: Boolean = true,
    scrim: Brush = defaultScrim(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current

    // La vidéo joue toujours sur l'écran de connexion, quelle que soit
    // la préférence d'animations ou le mode économie d'énergie. Seule
    // exception : la batterie critique (≤ LOW_BATTERY_THRESHOLD %) ou le
    // mode aperçu de l'IDE.
    val shouldAnimate = remember(inPreview) {
        !inPreview && context.allowsBackgroundMotion()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Toujours présente : sert de premier rendu, puis de repli.
        Image(
            painter = painterResource(posterRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (shouldAnimate) {
            LoopingVideoLayer(videoRes, startPositionMs)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrim),
        )

        content()
    }
}

@Composable
private fun LoopingVideoLayer(@RawRes videoRes: Int, startPositionMs: Int) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }

    // Fondu depuis l'image fixe vers la vidéo : évite le flash noir que
    // produit une TextureView tant que sa première image n'est pas décodée.
    val videoAlpha by animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.SLOW),
        label = "fondu-video",
    )

    val holder = remember { VideoHolder(context, videoRes, startPositionMs) { ready = true } }

    DisposableEffect(holder) {
        onDispose { holder.release() }
    }

    AndroidView(
        factory = { ctx -> holder.createView(ctx) },
        modifier = Modifier
            .fillMaxSize()
            .alpha(videoAlpha),
    )
}

/**
 * Détient le [MediaPlayer] et la [TextureView], et applique le recadrage
 * « centre » — `TextureView` étire par défaut, il faut corriger à la main
 * avec une matrice, contrairement à `ContentScale.Crop` d'une image.
 */
private class VideoHolder(
    context: Context,
    @RawRes private val videoRes: Int,
    private val startPositionMs: Int,
    private val onReady: () -> Unit,
) {
    private val uri: Uri = Uri.parse("android.resource://${context.packageName}/$videoRes")
    private var player: MediaPlayer? = null
    private var view: TextureView? = null

    fun createView(ctx: Context): TextureView {
        val textureView = TextureView(ctx)
        textureView.isOpaque = false
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                start(ctx, Surface(st), width, height)
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                applyCenterCrop(width, height)
            }

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                release()
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
        view = textureView
        return textureView
    }

    private fun start(ctx: Context, surface: Surface, width: Int, height: Int) {
        runCatching {
            MediaPlayer().apply {
                setDataSource(ctx, uri)
                setSurface(surface)
                isLooping = true
                setVolume(0f, 0f)   // la piste audio a été retirée à l'encodage
                setOnPreparedListener {
                    applyCenterCrop(width, height)
                    if (startPositionMs > 0) it.seekTo(startPositionMs)
                    it.start()
                    onReady()
                }
                prepareAsync()
                player = this
            }
        }
        // En cas d'échec (codec absent, fichier illisible), rien ne démarre :
        // l'image fixe déjà affichée dessous reste en place.
    }

    /**
     * Met la vidéo à l'échelle pour couvrir la vue sans déformation, puis
     * recentre — l'équivalent de `ContentScale.Crop`.
     */
    private fun applyCenterCrop(viewWidth: Int, viewHeight: Int) {
        val mp = player ?: return
        val v = view ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return
        val videoWidth = mp.videoWidth.takeIf { it > 0 } ?: return
        val videoHeight = mp.videoHeight.takeIf { it > 0 } ?: return

        val scaleX = viewWidth.toFloat() / videoWidth
        val scaleY = viewHeight.toFloat() / videoHeight
        val scale = maxOf(scaleX, scaleY)

        val scaledWidth = videoWidth * scale
        val scaledHeight = videoHeight * scale

        val matrix = Matrix().apply {
            setScale(scaledWidth / viewWidth, scaledHeight / viewHeight)
            postTranslate(
                (viewWidth - scaledWidth) / 2f,
                (viewHeight - scaledHeight) / 2f,
            )
        }
        v.setTransform(matrix)
    }

    fun release() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }
}

/**
 * Dégradé de protection par défaut.
 *
 * Plus dense en haut (barre d'état) et en bas (champs de saisie et boutons),
 * plus léger au centre pour laisser voir l'image. Sans ce dégradé, un
 * formulaire posé sur une vidéo devient illisible dès que la scène s'éclaircit.
 */
fun defaultScrim(): Brush = Brush.verticalGradient(
    // Haut : voilé juste ce qu'il faut pour la barre d'état.
    0.00f to Color(0x5C0B160E),
    // Tiers supérieur : la forêt reste visible, c'est le sujet de l'écran.
    0.32f to Color(0x2E0B160E),
    // Bas : les champs de saisie sont posés à même la vidéo, la protection
    // doit donc être franche sous eux, sinon le texte devient illisible dès
    // que la scène s'éclaircit.
    0.62f to Color(0xA3091309),
    1.00f to Color(0xF00A140C),
)

/**
 * La vidéo est-elle autorisée dans les conditions actuelles ?
 *
 * Seule condition d'arrêt : batterie critique (≤ [LOW_BATTERY_THRESHOLD] %).
 * À ce niveau, chaque cycle CPU compte pour permettre au forestier de finir
 * sa journée. Le mode économie d'énergie OS n'est intentionnellement pas
 * pris en compte : l'écran de connexion est le moment où GeoSylva se présente,
 * et la vidéo doit tourner quelles que soient les préférences système.
 */
private fun Context.allowsBackgroundMotion(): Boolean {
    val batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
    return level > LOW_BATTERY_THRESHOLD
}

/** Seuil critique : la vidéo s'arrête à 5 % et en dessous. */
private const val LOW_BATTERY_THRESHOLD = 5
