package com.littyl.digix.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.littyl.digix.R
import com.littyl.digix.ui.theme.Digix
import com.littyl.digix.ui.theme.DigixMotion
import com.littyl.digix.ui.theme.DigixShape
import com.littyl.digix.ui.theme.rememberHaptics

private val ControlShape = RoundedCornerShape(DigixShape.radius)
private val PillShape = RoundedCornerShape(percent = 50)

/** Scales a control down slightly under the finger, then springs back. */
@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val reduce = Digix.reduceMotion
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.97f else 1f,
        animationSpec = DigixMotion.quick(),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

enum class ButtonKind { PRIMARY, OUTLINE, GHOST, DESTRUCTIVE }

@Composable
fun DigixButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
) {
    val colors = Digix.colors
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val background by animateColorAsState(
        targetValue = when {
            kind != ButtonKind.PRIMARY -> Color.Transparent
            enabled -> colors.primary
            else -> colors.muted
        },
        animationSpec = DigixMotion.smooth(),
        label = "button background",
    )
    val content = when {
        !enabled -> colors.secondary
        kind == ButtonKind.PRIMARY -> colors.onPrimary
        kind == ButtonKind.DESTRUCTIVE -> colors.destructive
        else -> colors.foreground
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressScale(interaction)
            .clip(PillShape)
            .background(background)
            .then(
                if (kind == ButtonKind.OUTLINE || kind == ButtonKind.DESTRUCTIVE) {
                    Modifier.border(BorderStroke(DigixShape.hairline, colors.border), PillShape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = content),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.press()
                    onClick()
                },
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text, style = Digix.type.bodyMedium.copy(fontWeight = FontWeight.W600), color = content, textAlign = TextAlign.Center)
    }
}

/** A switch. Its thumb stretches under the finger, in the manner of Apple's switches. */
@Composable
fun DigixSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onValueChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                },
            )
            .semantics { if (description != null) contentDescription = description },
    ) {
        SwitchVisual(checked, interaction, live = false)
    }
}

/**
 * The drawn switch, for rows that are themselves the touch target. Only the master switch is
 * live: its red thumb means Digix is listening, and no other switch may use red.
 */
@Composable
fun SwitchVisual(checked: Boolean, interaction: MutableInteractionSource, live: Boolean, modifier: Modifier = Modifier) {
    val colors = Digix.colors
    val pressed by interaction.collectIsPressedAsState()
    val reduce = Digix.reduceMotion
    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val inset = 3.dp
    val thumbWidth by animateDpAsState(
        targetValue = if (pressed && !reduce) 32.dp else 26.dp,
        animationSpec = DigixMotion.quick(),
        label = "thumb width",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbWidth - inset else inset,
        animationSpec = if (reduce) DigixMotion.instant() else DigixMotion.quick(),
        label = "thumb offset",
    )
    val track by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.switchOff,
        animationSpec = DigixMotion.smooth(),
        label = "track",
    )
    val thumb by animateColorAsState(
        targetValue = when {
            !checked -> colors.quiet
            live -> colors.live
            else -> colors.onPrimary
        },
        animationSpec = DigixMotion.smooth(),
        label = "thumb",
    )
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.size(width = trackWidth + 8.dp, height = 48.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(width = trackWidth, height = trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(track),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(width = thumbWidth, height = 26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(thumb),
            )
        }
    }
}

/** A small dot that breathes while Digix is live, and becomes a hollow ring when it is off. */
@Composable
fun LiveDot(active: Boolean, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val colors = Digix.colors
    val reduce = Digix.reduceMotion
    val fill by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = DigixMotion.smooth(),
        label = "dot fill",
    )
    val breathing = rememberInfiniteTransition(label = "breathing")
    val pulse by breathing.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
        label = "pulse",
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size * 2)) {
        if (active && !reduce) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        val grow = 1f + pulse * 1.1f
                        scaleX = grow
                        scaleY = grow
                        alpha = (1f - pulse) * 0.35f
                    }
                    .clip(CircleShape)
                    .background(colors.live),
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.live.copy(alpha = fill))
                .border(1.5.dp, if (active) colors.live else colors.secondary, CircleShape),
        )
    }
}

/** A code in tabular numerals. Always reads left to right, including in right-to-left languages. */
@Composable
fun CodeBadge(code: String, modifier: Modifier = Modifier, highlighted: Boolean = false, quiet: Boolean = false) {
    val colors = Digix.colors
    val border by animateColorAsState(
        targetValue = if (highlighted) colors.live else colors.border,
        animationSpec = DigixMotion.smooth(),
        label = "badge border",
    )
    val description = stringResource(R.string.code_description, code.toList().joinToString(" "))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .semantics { contentDescription = description }
                .clip(PillShape)
                .background(colors.raised)
                .border(DigixShape.hairline, border, PillShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(code, style = Digix.type.code, color = if (quiet) colors.quiet else colors.foreground)
        }
    }
}

/** A group of rows on a solid surface, so text never sits on the halo. This is the app's only container. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(Digix.colors.muted),
        content = content,
    )
}

@Composable
fun GroupDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(DigixShape.hairline)
            .background(Digix.colors.divider),
    )
}

@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    @DrawableRes icon: Int? = null,
    titleColor: Color = Digix.colors.foreground,
    bodyColor: Color = Digix.colors.secondary,
    iconTint: Color = Digix.colors.foreground,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptics.press()
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 58.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (icon != null) ActionIcon(icon, tint = iconTint)
        Column(Modifier.weight(1f)) {
            Text(title, style = Digix.type.bodyMedium, color = titleColor)
            if (body != null) {
                Spacer(Modifier.height(1.dp))
                Text(body, style = Digix.type.subhead, color = bodyColor)
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun ActionIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, tint: Color = Digix.colors.foreground) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(DigixShape.radiusSmall))
            .background(Digix.colors.raised),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun Chevron() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = Digix.colors.secondary,
        modifier = Modifier
            .size(18.dp)
            .scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f),
    )
}

@Composable
fun IconAction(@DrawableRes icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, mirrorInRtl: Boolean = false) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .pressScale(interaction)
            .padding(2.dp)
            .clip(CircleShape)
            .background(Digix.colors.muted)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = description,
            ) {
                haptics.press()
                onClick()
            }
            .semantics { contentDescription = description },
    ) {
        CompositionLocalProvider(LocalContentColor provides Digix.colors.foreground) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .scale(scaleX = if (mirrorInRtl && rtl) -1f else 1f, scaleY = 1f),
            )
        }
    }
}

/**
 * The navigation bar stays pinned so Back is always reachable. Once the large title scrolls away,
 * a compact title fades in and a hairline appears, as Apple's navigation bars do.
 */
@Composable
fun PinnedBar(
    title: String,
    collapsed: Boolean,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val compact by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = DigixMotion.smooth(),
        label = "compact title",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(Digix.colors.background)
            .statusBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
        ) {
            if (onBack != null) {
                IconAction(
                    R.drawable.ic_arrow_left,
                    stringResource(R.string.action_back),
                    onBack,
                    mirrorInRtl = true,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            Text(
                title,
                style = Digix.type.headline,
                color = Digix.colors.foreground,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 104.dp)
                    .graphicsLayer {
                        alpha = compact
                        translationY = (1f - compact) * 8.dp.toPx()
                    }
                    .clearAndSetSemantics { },
            )
            if (trailing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    content = trailing,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(DigixShape.hairline)
                .graphicsLayer { alpha = compact }
                .background(Digix.colors.border),
        )
    }
}

/** The large title at the top of a screen's scrolling content. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = Digix.type.largeTitle,
        color = Digix.colors.foreground,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .semantics { heading() },
    )
}

@Composable
fun LazyListState.isTitleScrolledAway(): Boolean = remember(this) {
    derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > TITLE_SCROLL_PX }
}.value

@Composable
fun ScrollState.isTitleScrolledAway(): Boolean = remember(this) {
    derivedStateOf { value > TITLE_SCROLL_PX }
}.value

private const val TITLE_SCROLL_PX = 90

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(start = 2.dp),
    ) {
        Text(
            text,
            style = Digix.type.headline,
            color = Digix.colors.foreground,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke(this)
    }
}

/** A bullet of reassurance: a small check beside one plain sentence. */
@Composable
fun CheckedLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = Digix.colors.foreground,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Text(text, style = Digix.type.body, color = Digix.colors.foreground)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, @DrawableRes icon: Int = R.drawable.ic_watch) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Halo(Modifier.size(width = 240.dp, height = 150.dp)) {
            ActionIcon(icon)
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = Digix.type.headline, color = Digix.colors.foreground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = Digix.type.subhead, color = Digix.colors.secondary, textAlign = TextAlign.Center)
    }
}

/** Reveals an inline message with a spring, so it never makes the layout jump. */
@Composable
fun InlineMessage(text: String?, color: Color = Digix.colors.destructive, style: TextStyle = Digix.type.subhead, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = text != null, modifier = modifier) {
        Text(
            text.orEmpty(),
            style = style,
            color = color,
            modifier = Modifier
                .padding(top = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))
