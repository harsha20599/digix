package com.littyl.digix.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.littyl.digix.R
import java.util.Locale

@Immutable
data class DigixColors(
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    /** Grey for things that are off or paused, still readable on black. */
    val quiet: Color,
    /** The surface lists and groups sit on. */
    val muted: Color,
    /** A step above [muted], for icon tiles and controls inside a group. */
    val raised: Color,
    val border: Color,
    val divider: Color,
    /** The faint dots of the halo behind a screen's one big thing. */
    val halo: Color,
    val primary: Color,
    val onPrimary: Color,
    /** Red is the live key: it appears only where something is listening or happening. */
    val live: Color,
    val warning: Color,
    val destructive: Color,
    val switchOff: Color,
)

/** Digix is dark in every mode, so the halo and the red live key always read the same. */
private val Dark = DigixColors(
    background = Color(0xFF000000),
    foreground = Color(0xFFF2F2F2),
    secondary = Color(0xFFA1A1A1),
    quiet = Color(0xFF8C8C8C),
    muted = Color(0xFF111111),
    raised = Color(0xFF1C1C1C),
    border = Color(0xFF262626),
    divider = Color(0xFF1E1E1E),
    halo = Color(0xFF4A4A4A),
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF000000),
    live = Color(0xFFFF3B30),
    warning = Color(0xFFFFB000),
    destructive = Color(0xFFFF6961),
    switchOff = Color(0xFF2A2A2A),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(opticalSize: Float) = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(
            R.font.inter_variable,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    },
)

/** Optical sizes, as Apple's system type does: tighter forms for display, sturdier for text. */
private val InterDisplay = inter(32f)
private val InterText = inter(14f)

@OptIn(ExperimentalTextApi::class)
private fun variable(font: Int, weight: Int) = FontFamily(
    Font(font, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight))),
)

// These fonts are cut down to the few characters Digix draws with them (see res/font).
/** Dot-matrix type for the one big thing on a screen: ON, OFF, a code being set or dialed. */
private val Doto = variable(R.font.doto, 900)
/** Codes in lists: a monospace with clear digits, so 0101 and 0110 never blur together. */
private val Mono = variable(R.font.jetbrains_mono, 700)
private val Grotesk = variable(R.font.space_grotesk, 700)
private val Sora = variable(R.font.sora, 600)

/** Tightened tracking suits Latin display type, but damages cursive and complex scripts. */
private fun tracking(value: Float): TextUnit {
    val latinLike = Locale.getDefault().language !in setOf("ar", "fa", "ur", "he", "hi", "mr", "ne", "bn", "ta", "te", "kn", "ml", "gu", "pa", "th", "my", "km", "lo", "si")
    return if (latinLike) value.em else 0.em
}

@Immutable
object DigixType {
    val largeTitle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.W600, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = tracking(-0.022f))
    val title = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.W600, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = tracking(-0.02f))
    val headline = TextStyle(fontFamily = InterText, fontWeight = FontWeight.W600, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = tracking(-0.01f))
    val body = TextStyle(fontFamily = InterText, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = tracking(-0.006f))
    val bodyMedium = body.copy(fontWeight = FontWeight.W500)
    val subhead = TextStyle(fontFamily = InterText, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp)
    val subheadMedium = subhead.copy(fontWeight = FontWeight.W500)
    val caption = TextStyle(fontFamily = InterText, fontWeight = FontWeight.W500, fontSize = 13.sp, lineHeight = 18.sp)
    val code = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W700, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.05.em)
    val codeList = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W700, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.05.em)
    val codeLarge = TextStyle(fontFamily = Doto, fontWeight = FontWeight.W900, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = 0.06.em)
    /** The ON and OFF on the home screen. Only for those short Latin words: the font has no other letters. */
    val state = TextStyle(fontFamily = Doto, fontWeight = FontWeight.W900, fontSize = 92.sp, lineHeight = 92.sp, letterSpacing = 0.06.em)
    val wordmark = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.W700, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.045).em)
    val wordmarkStacked = TextStyle(fontFamily = Sora, fontWeight = FontWeight.W600, fontSize = 26.sp, lineHeight = 28.sp, letterSpacing = (-0.015).em)
}

object DigixShape {
    /** Groups and cards. */
    val radius: Dp = 20.dp
    val radiusSmall: Dp = 12.dp
    val hairline: Dp = 1.dp
}

/**
 * Every state change in the app uses one of these springs. They are interruptible and carry
 * velocity, so a change made mid-animation stays smooth.
 */
object DigixMotion {
    /** Controls answering a touch. */
    fun <T> quick(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 700f, visibilityThreshold = visibilityThreshold)

    /** Content changing state or moving between places. */
    fun <T> smooth(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = visibilityThreshold)

    /** Rare, earned moments such as a success check. */
    fun <T> lively(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.58f, stiffness = 420f, visibilityThreshold = visibilityThreshold)

    fun <T> instant(): FiniteAnimationSpec<T> = snap()
}

val LocalDigixColors = staticCompositionLocalOf { Dark }

/** True when the person has removed animations in system settings. */
val LocalReduceMotion = staticCompositionLocalOf { false }

object Digix {
    val colors: DigixColors
        @Composable get() = LocalDigixColors.current
    val type = DigixType
    val reduceMotion: Boolean
        @Composable get() = LocalReduceMotion.current
}

@Composable
fun DigixTheme(content: @Composable () -> Unit) {
    val colors = Dark
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val scheme = darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        background = colors.background,
        onBackground = colors.foreground,
        surface = colors.background,
        onSurface = colors.foreground,
        surfaceVariant = colors.muted,
        onSurfaceVariant = colors.secondary,
        outline = colors.border,
        error = colors.destructive,
    )
    CompositionLocalProvider(
        LocalDigixColors provides colors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
