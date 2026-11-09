package com.littyl.digix.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littyl.digix.R
import com.littyl.digix.ui.theme.Digix
import com.littyl.digix.ui.theme.DigixMotion
import kotlinx.coroutines.delay

/** What the watch on screen is showing. It stays in place across every onboarding step. */
sealed interface WatchFace {
    /** Digits type themselves out and the watch answers, on a loop. */
    data class Demo(val code: String) : WatchFace

    data class Icon(val icon: Int) : WatchFace

    /** A code to dial, with a ring breathing around it while it waits. */
    data class Waiting(val code: String) : WatchFace

    /** The code arrived. The ring closes and a check lands. */
    data object Success : WatchFace
}

/**
 * The onboarding's one authored element: a line-drawn watch that persists from the first step to
 * the last, so the whole flow reads as one continuous conversation with the object on your wrist.
 */
@Composable
fun WatchScene(face: WatchFace, watchSize: Dp, modifier: Modifier = Modifier) {
    val colors = Digix.colors
    val reduce = Digix.reduceMotion
    val diameter by animateDpAsState(
        targetValue = watchSize,
        animationSpec = if (reduce) DigixMotion.instant() else DigixMotion.smooth(),
        label = "watch size",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(diameter * 1.55f)
            .clearAndSetSemantics { },
    ) {
        val line = colors.foreground
        val strapFill = colors.muted
        val caseFill = colors.background
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = diameter.toPx() / 2
            val stroke = Stroke(width = (radius * 0.022f).coerceAtLeast(1.2.dp.toPx()), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val lugHalf = radius * 0.62f
            val endHalf = radius * 0.5f
            val strapReach = radius * 0.46f
            val corner = radius * 0.1f

            // A tapered strap, widest where it meets the case, in both directions.
            listOf(-1f, 1f).forEach { direction ->
                val start = center.y + direction * radius * 0.78f
                val end = center.y + direction * (radius + strapReach)
                val strap = Path().apply {
                    moveTo(center.x - lugHalf, start)
                    lineTo(center.x - endHalf, end - direction * corner)
                    quadraticTo(center.x - endHalf, end, center.x - endHalf + corner, end)
                    lineTo(center.x + endHalf - corner, end)
                    quadraticTo(center.x + endHalf, end, center.x + endHalf, end - direction * corner)
                    lineTo(center.x + lugHalf, start)
                    close()
                }
                drawPath(strap, strapFill)
                drawPath(strap, line, style = stroke)
            }

            // The crown, on the side where a wrist would reach for it.
            val crownWidth = radius * 0.1f
            val crownHeight = radius * 0.3f
            val crownLeft = center.x + radius - stroke.width / 2
            drawRoundRect(
                color = strapFill,
                topLeft = Offset(crownLeft, center.y - crownHeight / 2),
                size = Size(crownWidth, crownHeight),
                cornerRadius = CornerRadius(crownWidth / 2),
            )
            drawRoundRect(
                color = line,
                topLeft = Offset(crownLeft, center.y - crownHeight / 2),
                size = Size(crownWidth, crownHeight),
                cornerRadius = CornerRadius(crownWidth / 2),
                style = stroke,
            )

            drawCircle(color = caseFill, radius = radius, center = center)
            drawCircle(color = line, radius = radius, center = center, style = stroke)
        }
        Ring(face, diameter)
        AnimatedContent(
            targetState = face,
            contentKey = { it::class },
            transitionSpec = {
                (scaleIn(DigixMotion.lively(), initialScale = 0.72f) + fadeIn(tween(160))) togetherWith
                    (scaleOut(DigixMotion.quick(), targetScale = 0.8f) + fadeOut(tween(100)))
            },
            label = "watch face",
        ) { current ->
            when (current) {
                is WatchFace.Demo -> TypingCode(current.code)
                is WatchFace.Icon -> Icon(
                    painterResource(current.icon),
                    contentDescription = null,
                    tint = colors.foreground,
                    modifier = Modifier.size(diameter * 0.3f),
                )
                is WatchFace.Waiting -> CodeText(current.code, if (diameter < 160.dp) 32 else 36)
                WatchFace.Success -> Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.live,
                    modifier = Modifier.size(diameter * 0.34f),
                )
            }
        }
    }
}

@Composable
private fun Ring(face: WatchFace, diameter: Dp) {
    val colors = Digix.colors
    val reduce = Digix.reduceMotion
    val waiting = face is WatchFace.Waiting
    val success = face is WatchFace.Success

    val sweep = remember { Animatable(0f) }
    LaunchedEffect(success) {
        if (success) {
            if (reduce) sweep.snapTo(1f) else sweep.animateTo(1f, DigixMotion.smooth())
        } else {
            sweep.snapTo(0f)
        }
    }

    val breathing = rememberInfiniteTransition(label = "waiting")
    val rotation by breathing.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )

    Canvas(Modifier.size(diameter - diameter / 10)) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        when {
            success -> drawArc(
                color = colors.live,
                startAngle = -90f,
                sweepAngle = 360f * sweep.value,
                useCenter = false,
                style = stroke,
            )
            waiting -> {
                drawCircle(color = colors.border, style = Stroke(3.dp.toPx()))
                if (!reduce) {
                    drawArc(
                        color = colors.foreground,
                        startAngle = rotation - 90f,
                        sweepAngle = 72f,
                        useCenter = false,
                        style = stroke,
                    )
                }
            }
        }
    }
}

/** The welcome moment: digits arrive one by one, then the watch confirms, and it begins again. */
@Composable
private fun TypingCode(code: String) {
    val reduce = Digix.reduceMotion
    var shown by remember { mutableIntStateOf(if (reduce) code.length else 0) }
    var confirmed by remember { mutableIntStateOf(0) }
    LaunchedEffect(code, reduce) {
        if (reduce) return@LaunchedEffect
        while (true) {
            shown = 0
            confirmed = 0
            delay(650)
            for (i in 1..code.length) {
                shown = i
                delay(240)
            }
            delay(380)
            confirmed = 1
            delay(1500)
        }
    }
    AnimatedContent(
        targetState = confirmed == 1,
        transitionSpec = {
            (scaleIn(DigixMotion.lively(), initialScale = 0.6f) + fadeIn(tween(120))) togetherWith
                (scaleOut(DigixMotion.quick(), targetScale = 0.8f) + fadeOut(tween(90)))
        },
        label = "demo confirm",
    ) { done ->
        if (done) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Digix.colors.live,
                modifier = Modifier.size(64.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    code.forEachIndexed { index, digit ->
                        TypedDigit(digit, visible = index < shown)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypedDigit(digit: Char, visible: Boolean) {
    val reduce = Digix.reduceMotion
    val scale = remember { Animatable(if (visible) 1f else 0.4f) }
    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible && !reduce) {
            alpha.animateTo(1f, tween(90))
        } else {
            alpha.snapTo(if (visible) 1f else 0f)
        }
    }
    LaunchedEffect(visible) {
        if (visible && !reduce) scale.animateTo(1f, DigixMotion.lively()) else scale.snapTo(if (visible) 1f else 0.4f)
    }
    Text(
        digit.toString(),
        style = Digix.type.codeLarge.copy(fontSize = 38.sp, lineHeight = 44.sp),
        color = Digix.colors.foreground,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        },
    )
}

@Composable
private fun CodeText(code: String, sizeSp: Int) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            code,
            style = Digix.type.codeLarge.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp + 6).sp),
            color = Digix.colors.foreground,
        )
    }
}
