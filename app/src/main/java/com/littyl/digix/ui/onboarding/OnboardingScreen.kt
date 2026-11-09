package com.littyl.digix.ui.onboarding

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.littyl.digix.R
import com.littyl.digix.core.RunStatus
import com.littyl.digix.core.Store
import com.littyl.digix.engine.Notifications
import com.littyl.digix.engine.PhoneState
import com.littyl.digix.ui.ButtonKind
import com.littyl.digix.ui.CheckedLine
import com.littyl.digix.ui.CodeBadge
import com.littyl.digix.ui.DigixButton
import com.littyl.digix.ui.Group
import com.littyl.digix.ui.GroupDivider
import com.littyl.digix.ui.GroupRow
import com.littyl.digix.ui.Halo
import com.littyl.digix.ui.LiveDot
import com.littyl.digix.ui.Readiness
import com.littyl.digix.ui.StackedWordmark
import com.littyl.digix.ui.VSpace
import com.littyl.digix.ui.rememberResumeTick
import com.littyl.digix.ui.theme.Digix
import com.littyl.digix.ui.theme.DigixMotion
import com.littyl.digix.ui.theme.rememberHaptics
import kotlinx.coroutines.delay

// The order earns each request: only the permission the first dial needs comes before it.
private const val STEP_WELCOME = 0
private const val STEP_CALLS = 1
private const val STEP_TRY = 2
private const val STEP_BACKGROUND = 3
private const val STEP_NOTIFY = 4
private const val STEP_DONE = 5
private const val STEP_COUNT = 6
private const val TRY_CODE = "0101"
private const val HINT_DELAY_MS = 25_000L

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    // A saved trail, so Back retraces where this person went rather than where they skipped.
    var trail by rememberSaveable { mutableStateOf(listOf(STEP_WELCOME)) }
    val step = trail.last()
    var forward by remember { mutableStateOf(true) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var succeeded by rememberSaveable { mutableStateOf(false) }
    var failure by rememberSaveable { mutableStateOf<String?>(null) }
    val reduce = Digix.reduceMotion
    val tick = rememberResumeTick()
    val roleHeld = remember(tick, step) { Readiness.holdsCallRole(context) }
    val allCodes by Store.codes.collectAsStateWithLifecycle()
    val allReady = remember(tick, step, allCodes) { allCodes.all { Readiness.isReady(context, it) } }

    LaunchedEffect(Unit) {
        Store.prepareFirstRun()
    }

    fun go(next: Int) {
        forward = true
        trail = trail + next
    }

    // Never asks for something this phone doesn't need or has already allowed.
    fun afterTry() {
        when {
            !Readiness.canStartInBackground(context) -> go(STEP_BACKGROUND)
            needsNotificationPrompt(context) -> go(STEP_NOTIFY)
            else -> go(STEP_DONE)
        }
    }

    fun afterBackground() {
        if (needsNotificationPrompt(context)) go(STEP_NOTIFY) else go(STEP_DONE)
    }

    BackHandler(enabled = trail.size > 1) {
        forward = false
        trail = trail.dropLast(1)
    }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Readiness.canStartInBackground(context)) haptics.confirm()
        afterBackground()
    }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Optional: without it replies still work, but the watch sees the dial fail first.
        go(STEP_TRY)
    }
    fun afterRole() {
        if (PhoneState.canRead(context)) go(STEP_TRY) else phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE)
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Readiness.holdsCallRole(context)) {
            denied = false
            haptics.confirm()
            afterRole()
        } else {
            denied = true
            haptics.reject()
        }
    }

    fun requestRole() {
        val manager = context.getSystemService(RoleManager::class.java)
        roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION))
    }

    // The aha moment: a real code from the real watch, and it celebrates only a real result.
    LaunchedEffect(step) {
        if (step != STEP_TRY) return@LaunchedEffect
        Store.entries.collect { entry ->
            if (entry.code != TRY_CODE) return@collect
            if (entry.status == RunStatus.DONE) {
                haptics.codeCaught()
                failure = null
                succeeded = true
            } else {
                haptics.reject()
                failure = entry.message
            }
        }
    }

    val face = when (step) {
        STEP_WELCOME -> WatchFace.Demo(TRY_CODE)
        STEP_CALLS -> WatchFace.Icon(R.drawable.ic_shield_check)
        STEP_TRY -> if (succeeded) WatchFace.Success else WatchFace.Waiting(TRY_CODE)
        STEP_BACKGROUND -> WatchFace.Icon(R.drawable.ic_zap)
        STEP_NOTIFY -> WatchFace.Icon(R.drawable.ic_bell_ring)
        else -> when {
            !roleHeld -> WatchFace.Icon(R.drawable.ic_circle_alert)
            allReady -> WatchFace.Success
            else -> WatchFace.Icon(R.drawable.ic_check)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Digix.colors.background)
            .safeDrawingPadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // The first screen introduces the name; after that, the dots show the way through.
            AnimatedContent(
                targetState = step == STEP_WELCOME,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "onboarding top",
            ) { welcome ->
                if (welcome) StackedWordmark() else StepDots(step)
            }
            Halo(Modifier.fillMaxWidth()) {
                WatchScene(
                    face,
                    watchSize = when (step) {
                        STEP_WELCOME -> 184.dp
                        STEP_DONE -> 92.dp
                        else -> 150.dp
                    },
                    modifier = Modifier.padding(vertical = 30.dp),
                )
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val direction = if (forward) 1 else -1
                    val spec = DigixMotion.smooth(IntOffset.VisibilityThreshold)
                    if (reduce) {
                        fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                    } else {
                        (slideInHorizontally(spec) { it / 5 * direction } + fadeIn(tween(220, delayMillis = 60))) togetherWith
                            (slideOutHorizontally(spec) { -it / 5 * direction } + fadeOut(tween(120)))
                    }
                },
                contentAlignment = Alignment.BottomCenter,
                label = "onboarding step",
            ) { current ->
                when (current) {
                    STEP_WELCOME -> Page(
                        title = stringResource(R.string.onboard_welcome_title),
                        body = stringResource(R.string.onboard_welcome_body),
                    ) {
                        DigixButton(stringResource(R.string.onboard_welcome_cta), { go(STEP_CALLS) }, Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.onboard_welcome_note),
                            style = Digix.type.subhead,
                            color = Digix.colors.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .padding(top = 12.dp),
                        )
                    }
                    STEP_CALLS -> Page(
                        title = stringResource(R.string.onboard_calls_title),
                        body = stringResource(R.string.onboard_calls_body),
                        extra = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CheckedLine(stringResource(R.string.onboard_calls_point_1))
                                CheckedLine(stringResource(R.string.onboard_calls_point_2))
                                CheckedLine(stringResource(R.string.onboard_calls_point_3))
                                CheckedLine(stringResource(R.string.onboard_calls_point_4))
                            }
                        },
                    ) {
                        AnimatedVisibility(visible = denied) {
                            Text(
                                stringResource(R.string.onboard_calls_denied),
                                style = Digix.type.subhead,
                                color = Digix.colors.destructive,
                                modifier = Modifier
                                    .padding(bottom = 10.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        Text(
                            stringResource(R.string.onboard_calls_next),
                            style = Digix.type.subhead,
                            color = Digix.colors.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        )
                        DigixButton(
                            stringResource(R.string.onboard_calls_cta),
                            { if (Readiness.holdsCallRole(context)) afterRole() else requestRole() },
                            Modifier.fillMaxWidth(),
                        )
                        DigixButton(stringResource(R.string.action_not_now), { go(STEP_DONE) }, Modifier.fillMaxWidth(), kind = ButtonKind.GHOST)
                    }
                    STEP_TRY -> TryStep(succeeded = succeeded, failure = failure, onContinue = { afterTry() })
                    STEP_BACKGROUND -> Page(
                        title = stringResource(R.string.onboard_background_title),
                        body = stringResource(if (succeeded) R.string.onboard_background_body_after_try else R.string.onboard_background_body),
                        extra = { CheckedLine(stringResource(R.string.onboard_background_point)) },
                    ) {
                        DigixButton(
                            stringResource(R.string.onboard_background_cta),
                            {
                                backgroundLauncher.launch(Readiness.backgroundStartIntent(context))
                            },
                            Modifier.fillMaxWidth(),
                        )
                        DigixButton(stringResource(R.string.action_not_now), { afterBackground() }, Modifier.fillMaxWidth(), kind = ButtonKind.GHOST)
                    }
                    STEP_NOTIFY -> Page(
                        title = stringResource(R.string.onboard_notify_title),
                        body = stringResource(R.string.onboard_notify_body),
                    ) {
                        DigixButton(
                            stringResource(R.string.onboard_notify_cta),
                            {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                                go(STEP_DONE)
                            },
                            Modifier.fillMaxWidth(),
                        )
                        DigixButton(stringResource(R.string.action_not_now), { go(STEP_DONE) }, Modifier.fillMaxWidth(), kind = ButtonKind.GHOST)
                    }
                    else -> DoneStep(roleHeld = roleHeld, onAllow = { requestRole() }, onFinished = onFinished)
                }
            }
        }
    }
}

/** At this target Android asks by itself when the app first runs, so only a refusal needs a step. */
private fun needsNotificationPrompt(context: Context): Boolean = !Notifications.canPost(context)

@Composable
private fun TryStep(succeeded: Boolean, failure: String?, onContinue: () -> Unit) {
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(HINT_DELAY_MS)
        showHint = true
    }
    AnimatedContent(
        targetState = succeeded,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
        label = "try result",
    ) { worked ->
        if (worked) {
            Page(
                title = stringResource(R.string.onboard_success_title),
                body = stringResource(R.string.onboard_success_body, TRY_CODE),
            ) {
                DigixButton(stringResource(R.string.action_continue), onContinue, Modifier.fillMaxWidth())
            }
        } else {
            Page(
                title = stringResource(R.string.onboard_try_title),
                body = stringResource(R.string.onboard_try_body, TRY_CODE),
                extra = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(active = true)
                            Text(
                                stringResource(R.string.onboard_try_waiting),
                                style = Digix.type.subheadMedium,
                                color = Digix.colors.foreground,
                            )
                        }
                        AnimatedVisibility(
                            visible = failure != null,
                            enter = fadeIn() + expandVertically(DigixMotion.smooth()),
                            exit = fadeOut() + shrinkVertically(DigixMotion.smooth()),
                        ) {
                            Text(
                                stringResource(R.string.onboard_try_failed, failure.orEmpty(), TRY_CODE),
                                style = Digix.type.subhead,
                                color = Digix.colors.destructive,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        AnimatedVisibility(
                            visible = showHint && failure == null,
                            enter = fadeIn() + expandVertically(DigixMotion.smooth()),
                            exit = fadeOut() + shrinkVertically(DigixMotion.smooth()),
                        ) {
                            Text(
                                stringResource(R.string.onboard_try_hint),
                                style = Digix.type.subhead,
                                color = Digix.colors.secondary,
                            )
                        }
                    }
                },
            ) {
                DigixButton(stringResource(R.string.onboard_try_skip), onContinue, Modifier.fillMaxWidth(), kind = ButtonKind.OUTLINE)
            }
        }
    }
}

/** Only says "You're set" when it is true. */
@Composable
private fun DoneStep(roleHeld: Boolean, onAllow: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val codes by Store.codes.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    val allReady = remember(tick, codes) { codes.all { Readiness.isReady(context, it) } }
    if (!roleHeld) {
        Page(
            title = stringResource(R.string.onboard_notset_title),
            body = stringResource(R.string.home_role_body),
        ) {
            DigixButton(stringResource(R.string.home_role_cta), onAllow, Modifier.fillMaxWidth())
            DigixButton(stringResource(R.string.onboard_continue_anyway), onFinished, Modifier.fillMaxWidth(), kind = ButtonKind.GHOST)
        }
        return
    }
    Page(
        title = stringResource(if (allReady) R.string.onboard_done_title else R.string.onboard_almost_title),
        body = stringResource(if (allReady) R.string.onboard_done_body else R.string.onboard_done_partial),
        extra = {
            Group {
                codes.forEachIndexed { index, binding ->
                    if (index > 0) GroupDivider()
                    val ready = remember(tick, binding) { Readiness.isReady(context, binding) }
                    GroupRow(
                        title = stringResource(binding.action.title),
                        body = if (ready) null else stringResource(R.string.status_needs_setup),
                        bodyColor = Digix.colors.warning,
                        icon = binding.action.icon,
                        trailing = { CodeBadge(binding.code) },
                    )
                }
            }
        },
    ) {
        DigixButton(stringResource(R.string.onboard_done_cta), onFinished, Modifier.fillMaxWidth())
    }
}

/** Every step shares one shape: a heading, one short paragraph, optional proof, and its actions. */
@Composable
private fun Page(
    title: String,
    body: String,
    extra: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = Digix.type.title,
            color = Digix.colors.foreground,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        VSpace(8.dp)
        Text(body, style = Digix.type.body, color = Digix.colors.secondary)
        if (extra != null) {
            VSpace(18.dp)
            extra()
        }
        VSpace(24.dp)
        actions()
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(STEP_COUNT) { index ->
            val width by animateDpAsState(
                targetValue = if (index == step) 20.dp else 6.dp,
                animationSpec = DigixMotion.smooth(),
                label = "dot width",
            )
            val color by animateColorAsState(
                targetValue = if (index <= step) Digix.colors.foreground else Digix.colors.quiet.copy(alpha = 0.45f),
                animationSpec = DigixMotion.smooth(),
                label = "dot color",
            )
            Box(
                Modifier
                    .size(width = width, height = 6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
