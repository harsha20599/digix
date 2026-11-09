package com.littyl.digix.ui.screens

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.littyl.digix.R
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.CodeBinding
import com.littyl.digix.core.RunEntry
import com.littyl.digix.core.RunStatus
import com.littyl.digix.core.SosConfig
import com.littyl.digix.core.Store
import com.littyl.digix.engine.PhoneState
import com.littyl.digix.engine.SosService
import com.littyl.digix.ui.ButtonKind
import com.littyl.digix.ui.Chevron
import com.littyl.digix.ui.DigixButton
import com.littyl.digix.ui.Group
import com.littyl.digix.ui.GroupDivider
import com.littyl.digix.ui.GroupRow
import com.littyl.digix.ui.Halo
import com.littyl.digix.ui.IconAction
import com.littyl.digix.ui.LiveDot
import com.littyl.digix.ui.Readiness
import com.littyl.digix.ui.SwitchVisual
import com.littyl.digix.ui.VSpace
import com.littyl.digix.ui.Wordmark
import com.littyl.digix.ui.rememberResumeTick
import com.littyl.digix.ui.theme.Digix
import com.littyl.digix.ui.theme.DigixMotion
import com.littyl.digix.ui.theme.DigixShape
import com.littyl.digix.ui.theme.rememberHaptics
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onOpenCode: (String) -> Unit,
    onAdd: () -> Unit,
    onNew: (ActionType) -> Unit,
    onActivity: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val enabled by Store.enabled.collectAsStateWithLifecycle()
    val codes by Store.codes.collectAsStateWithLifecycle()
    val log by Store.log.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    var roleGranted by remember { mutableStateOf(false) }
    val roleHeld = roleGranted || remember(tick) { Readiness.holdsCallRole(context) }
    val needingSetup = remember(tick, codes) { codes.count { !Readiness.isReady(context, it) } }
    val haptics = rememberHaptics()

    // A row only confirms what really happened: a check and the signature haptic mean it ran.
    var confirmed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        Store.entries.collect { entry ->
            if (entry.status == RunStatus.DONE) {
                haptics.codeCaught()
                confirmed = entry.code
                delay(CONFIRM_MS)
                confirmed = null
            } else {
                haptics.reject()
            }
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        roleGranted = result.resultCode == Activity.RESULT_OK
    }
    val spoken by Store.spokenReplies.collectAsStateWithLifecycle()
    val sosActive by SosConfig.active.collectAsStateWithLifecycle()
    var phoneGranted by remember { mutableStateOf(false) }
    val phoneAccess = phoneGranted || remember(tick) { PhoneState.canRead(context) }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        phoneGranted = granted
    }
    val list = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeBar(enabled, onActivity = onActivity, onSettings = onSettings)
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        StateHero(enabled, roleHeld, needingSetup)
                        AnimatedVisibility(
                            visible = !roleHeld,
                            enter = fadeIn() + expandVertically(DigixMotion.smooth()),
                            exit = fadeOut() + shrinkVertically(DigixMotion.smooth()),
                        ) {
                            Banner(
                                title = stringResource(R.string.home_role_title),
                                body = stringResource(R.string.home_role_body),
                                onAllow = {
                                    val manager = context.getSystemService(RoleManager::class.java)
                                    roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION))
                                },
                            )
                        }
                        AnimatedVisibility(
                            visible = sosActive,
                            enter = fadeIn() + expandVertically(DigixMotion.smooth()),
                            exit = fadeOut() + shrinkVertically(DigixMotion.smooth()),
                        ) {
                            Banner(
                                title = stringResource(R.string.home_sos_title),
                                body = stringResource(R.string.home_sos_body),
                                actionLabel = stringResource(R.string.action_stop),
                                onAllow = { SosService.stop(context) },
                            )
                        }
                        AnimatedVisibility(
                            visible = roleHeld && spoken && !phoneAccess,
                            enter = fadeIn() + expandVertically(DigixMotion.smooth()),
                            exit = fadeOut() + shrinkVertically(DigixMotion.smooth()),
                        ) {
                            Banner(
                                title = stringResource(R.string.home_phone_title),
                                body = stringResource(R.string.home_phone_body),
                                onAllow = { phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                            )
                        }
                    }
                }
                if (codes.isEmpty()) {
                    item { NoCodes(onAdd = onAdd, onNew = onNew) }
                } else {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
                        ) {
                            Text(
                                stringResource(R.string.home_codes),
                                style = Digix.type.headline,
                                color = Digix.colors.foreground,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { heading() },
                            )
                            AddButton(onAdd)
                        }
                    }
                    item {
                        Group(Modifier.padding(horizontal = 16.dp)) {
                            codes.forEachIndexed { index, binding ->
                                if (index > 0) GroupDivider()
                                CodeRow(
                                    binding = binding,
                                    last = log.firstOrNull { it.code == binding.code },
                                    ready = remember(tick, binding) { Readiness.isReady(context, binding) },
                                    masterOn = enabled,
                                    confirmed = confirmed == binding.code,
                                    onClick = { onOpenCode(binding.code) },
                                )
                            }
                        }
                    }
                }
                item { VSpace(96.dp) }
            }
        }
        UndoBar(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun HomeBar(enabled: Boolean, onActivity: () -> Unit, onSettings: () -> Unit) {
    // The live key in the mark goes grey with the switch, so the logo itself says whether Digix is on.
    val key by animateColorAsState(
        targetValue = if (enabled) Digix.colors.live else Digix.colors.quiet,
        animationSpec = DigixMotion.smooth(),
        label = "logo key",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Digix.colors.background)
            .statusBarsPadding()
            .height(60.dp)
            .padding(start = 18.dp, end = 10.dp),
    ) {
        Wordmark(Modifier.weight(1f), live = key)
        IconAction(R.drawable.ic_history, stringResource(R.string.home_activity), onActivity)
        IconAction(R.drawable.ic_settings, stringResource(R.string.action_settings), onSettings)
    }
}

/**
 * ON or OFF in dot type over the halo, with the master switch in the pill below it. The pill is
 * the touch target and reads as one switch to TalkBack.
 */
@Composable
private fun StateHero(enabled: Boolean, roleHeld: Boolean, needingSetup: Int) {
    val haptics = rememberHaptics()
    val colors = Digix.colors
    val interaction = remember { MutableInteractionSource() }
    val name = stringResource(R.string.home_master_description)
    val body = when {
        !enabled -> stringResource(R.string.home_off_body)
        !roleHeld -> stringResource(R.string.home_no_role_body)
        needingSetup > 0 -> pluralStringResource(R.plurals.home_need_setup, needingSetup, needingSetup)
        else -> stringResource(R.string.home_on_body)
    }
    val strength by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.45f,
        animationSpec = DigixMotion.smooth(),
        label = "halo strength",
    )
    val wordColor by animateColorAsState(
        targetValue = if (enabled) colors.foreground else colors.quiet,
        animationSpec = DigixMotion.smooth(),
        label = "state colour",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Halo(
            strength = strength,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            AnimatedContent(
                targetState = enabled,
                transitionSpec = {
                    (slideInVertically(DigixMotion.smooth()) { it / 3 } + fadeIn()) togetherWith
                        (slideOutVertically(DigixMotion.smooth()) { -it / 3 } + fadeOut())
                },
                label = "state word",
            ) { on ->
                val word = stringResource(if (on) R.string.home_state_on else R.string.home_state_off)
                // The dot font only has the Latin ON and OFF; other languages use the display face.
                val dotted = word.all { it.code < 128 }
                Text(
                    if (dotted) word.uppercase() else word,
                    style = if (dotted) Digix.type.state else Digix.type.largeTitle,
                    color = wordColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.muted)
                .border(DigixShape.hairline, colors.border, RoundedCornerShape(percent = 50))
                .toggleable(
                    value = enabled,
                    interactionSource = interaction,
                    indication = ripple(),
                    role = Role.Switch,
                    onValueChange = {
                        haptics.toggle(it)
                        Store.setEnabled(it)
                    },
                )
                .semantics { contentDescription = name }
                .padding(start = 8.dp, end = 2.dp),
        ) {
            LiveDot(enabled && roleHeld, size = 8.dp)
            AnimatedContent(
                targetState = body,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
                label = "state body",
                modifier = Modifier.weight(1f, fill = false),
            ) { text ->
                Text(text, style = Digix.type.subheadMedium, color = colors.foreground)
            }
            SwitchVisual(enabled, interaction, live = true)
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val description = stringResource(R.string.home_add_code)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Digix.colors.primary)
            .clickable(role = Role.Button, onClickLabel = description) {
                haptics.press()
                onClick()
            }
            .semantics { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = Digix.colors.onPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Banner(title: String, body: String, actionLabel: String = stringResource(R.string.home_role_cta), onAllow: () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Group {
            GroupRow(
                title = title,
                body = body,
                icon = R.drawable.ic_circle_alert,
                trailing = {
                    DigixButton(actionLabel, onAllow, kind = ButtonKind.OUTLINE)
                },
            )
        }
    }
}

/** The code leads each row, in a monospace that keeps every digit distinct. */
@Composable
private fun CodeRow(
    binding: CodeBinding,
    last: RunEntry?,
    ready: Boolean,
    masterOn: Boolean,
    confirmed: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = Digix.colors
    val haptics = rememberHaptics()
    val wash by animateColorAsState(
        targetValue = colors.foreground.copy(alpha = if (confirmed) 0.08f else 0f),
        animationSpec = DigixMotion.smooth(),
        label = "row wash",
    )
    // Off quiets the row without losing contrast: everything stays readable and tappable.
    val titleColor by animateColorAsState(
        targetValue = if (masterOn) colors.foreground else colors.secondary,
        animationSpec = DigixMotion.smooth(),
        label = "row title",
    )
    val codeColor by animateColorAsState(
        targetValue = if (masterOn) colors.foreground else colors.quiet,
        animationSpec = DigixMotion.smooth(),
        label = "row code",
    )
    val status = when {
        !ready -> stringResource(R.string.status_needs_setup)
        last != null -> statusLine(context, last)
        else -> stringResource(R.string.status_not_used)
    }
    val codeDescription = stringResource(R.string.code_description, binding.code.toList().joinToString(" "))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(wash)
            .clickable {
                haptics.press()
                onClick()
            }
            .heightIn(min = 64.dp)
            .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
    ) {
        // Codes are numbers on a dial pad, so they read left to right in every language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                binding.code,
                style = Digix.type.codeList,
                color = codeColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .width(62.dp)
                    .semantics { contentDescription = codeDescription },
            )
        }
        Icon(
            painterResource(binding.action.icon),
            contentDescription = null,
            tint = colors.secondary,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(googleHomeTitle(binding) ?: stringResource(binding.action.title), style = Digix.type.bodyMedium, color = titleColor)
            Text(status, style = Digix.type.subhead, color = if (!ready) colors.warning else colors.secondary)
        }
        AnimatedContent(
            targetState = confirmed,
            transitionSpec = {
                (scaleIn(DigixMotion.lively(), initialScale = 0.6f) + fadeIn()) togetherWith
                    (scaleOut(DigixMotion.quick(), targetScale = 0.8f) + fadeOut())
            },
            label = "row confirm",
        ) { showCheck ->
            if (showCheck) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.foreground,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Chevron()
            }
        }
    }
}

/** With no codes yet, the halo frames the one thing to do, and a few common starts are a tap away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoCodes(onAdd: () -> Unit, onNew: (ActionType) -> Unit) {
    val colors = Digix.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        // One halo per screen: here it stays on ON, so the plus is drawn in plain dots.
        VSpace(28.dp)
        Text("+", style = Digix.type.codeLarge.copy(fontSize = 72.sp, lineHeight = 72.sp), color = colors.quiet, modifier = Modifier.clearAndSetSemantics { })
        VSpace(14.dp)
        Text(
            stringResource(R.string.home_empty_title),
            style = Digix.type.title,
            color = colors.foreground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        VSpace(6.dp)
        Text(
            stringResource(R.string.home_empty_body),
            style = Digix.type.body,
            color = colors.secondary,
            textAlign = TextAlign.Center,
        )
        VSpace(26.dp)
        Text(
            stringResource(R.string.home_start_with),
            style = Digix.type.caption,
            color = colors.secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(10.dp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(ActionType.FLASHLIGHT, ActionType.FIND_PHONE, ActionType.BATTERY).forEach { action ->
                DigixButton(stringResource(action.title), { onNew(action) }, kind = ButtonKind.OUTLINE)
            }
        }
        VSpace(16.dp)
        DigixButton(stringResource(R.string.home_add_code), onAdd, Modifier.fillMaxWidth())
    }
}

/** Deleting is instant, so it is also undoable: a stray delete could otherwise place a real call. */
@Composable
private fun UndoBar(modifier: Modifier = Modifier) {
    val undoable by Store.undoable.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        onDispose { Store.dismissUndo() }
    }
    LaunchedEffect(undoable) {
        if (undoable != null) {
            delay(UNDO_MS)
            Store.dismissUndo()
        }
    }
    AnimatedVisibility(
        visible = undoable != null,
        enter = slideInVertically(DigixMotion.smooth()) { it } + fadeIn(),
        exit = slideOutVertically(DigixMotion.smooth()) { it } + fadeOut(),
        modifier = modifier,
    ) {
        val colors = Digix.colors
        val shape = RoundedCornerShape(DigixShape.radius)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .background(colors.raised, shape)
                .border(DigixShape.hairline, colors.border, shape)
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text(
                stringResource(R.string.home_deleted, undoable?.first?.code.orEmpty()),
                style = Digix.type.body,
                color = colors.foreground,
                modifier = Modifier.weight(1f),
            )
            DigixButton(stringResource(R.string.action_undo), { Store.undoDelete() }, kind = ButtonKind.GHOST)
        }
    }
}

/** "Just now" under a minute, then the system's own localized relative time. */
fun relativeTime(context: Context, timeMillis: Long): String {
    val now = System.currentTimeMillis()
    if (now - timeMillis < DateUtils.MINUTE_IN_MILLIS) return context.getString(R.string.time_just_now)
    return DateUtils.getRelativeTimeSpanString(
        timeMillis,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

fun statusLine(context: Context, entry: RunEntry): String {
    val time = relativeTime(context, entry.timeMillis)
    val format = when (entry.status) {
        RunStatus.DONE -> R.string.status_ran
        RunStatus.STOPPED_OFF -> R.string.status_stopped
        RunStatus.NEEDS_SETUP, RunStatus.FAILED -> R.string.status_failed
    }
    return context.getString(format, time)
}

private const val CONFIRM_MS = 1600L
private const val UNDO_MS = 6000L
