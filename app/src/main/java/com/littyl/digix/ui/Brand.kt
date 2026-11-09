package com.littyl.digix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.littyl.digix.R
import com.littyl.digix.ui.theme.Digix
import kotlin.math.sqrt

/**
 * A field of dots that fades out from the middle, drawn behind a screen's one big thing.
 * Its centre is kept clear, so whatever sits on it is always read against plain black.
 *
 * [strength] dims the whole field, so a screen can quiet it when Digix is off.
 */
@Composable
fun Halo(
    modifier: Modifier = Modifier,
    strength: Float = 1f,
    spacing: Dp = 12.dp,
    dotRadius: Dp = 1.6.dp,
    clearCentre: Float = 0.34f,
    content: @Composable BoxScope.() -> Unit,
) {
    val dot = Digix.colors.halo
    Box(modifier, contentAlignment = Alignment.Center) {
        // Sized to the box, so the halo works inside scrolling columns too.
        Canvas(Modifier.matchParentSize()) {
            val step = spacing.toPx()
            val radius = dotRadius.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            // The field is an ellipse that fills the box, so a wide box gives a wide halo.
            val rx = size.width / 2f
            val ry = size.height / 2f
            val columns = (size.width / step).toInt() + 1
            val rows = (size.height / step).toInt() + 1
            val startX = cx - (columns / 2) * step
            val startY = cy - (rows / 2) * step
            for (row in 0..rows) {
                for (column in 0..columns) {
                    val x = startX + column * step
                    val y = startY + row * step
                    val dx = (x - cx) / rx
                    val dy = (y - cy) / ry
                    val distance = sqrt(dx * dx + dy * dy)
                    val outer = 1f - smooth(0.45f, 1f, distance)
                    val inner = smooth(clearCentre * 0.55f, clearCentre, distance)
                    val alpha = outer * inner * strength
                    if (alpha > 0.02f) drawCircle(dot.copy(alpha = alpha), radius, Offset(x, y))
                }
            }
        }
        content()
    }
}

private fun smooth(from: Float, to: Float, value: Float): Float {
    val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The Digix mark: a dial pad of dots whose middle key is live. Drawn, so it stays crisp at any size. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier, live: Color = Digix.colors.live) {
    val dots = Digix.colors.foreground
    val stroke = Digix.colors.border
    Canvas(modifier.size(size)) {
        val unit = this.size.width / 40f
        drawRoundRect(Color(0xFF09090B), cornerRadius = CornerRadius(11f * unit))
        drawRoundRect(
            stroke,
            topLeft = Offset(0.5f * unit, 0.5f * unit),
            size = Size(39f * unit, 39f * unit),
            cornerRadius = CornerRadius(10.5f * unit),
            style = Stroke(width = unit),
        )
        for (row in 0..2) {
            for (column in 0..2) {
                val centre = Offset((12f + column * 8f) * unit, (12f + row * 8f) * unit)
                if (row == 1 && column == 1) {
                    drawCircle(live, 4f * unit, centre)
                } else {
                    drawCircle(dots, 2.6f * unit, centre)
                }
            }
        }
    }
}

/** The mark beside the name, for the top of the home screen. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, markSize: Dp = 36.dp, live: Color = Digix.colors.live) {
    val name = stringResource(R.string.app_name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.semantics { contentDescription = name; heading() },
    ) {
        LogoMark(markSize, live = live)
        Text(
            name.lowercase(),
            style = Digix.type.wordmark,
            color = Digix.colors.foreground,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** The mark over the name, for the first thing a person sees. */
@Composable
fun StackedWordmark(modifier: Modifier = Modifier) {
    val name = stringResource(R.string.app_name)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.semantics { contentDescription = name },
    ) {
        LogoMark(48.dp)
        Text(
            name.lowercase(),
            style = Digix.type.wordmarkStacked,
            color = Digix.colors.foreground,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
