package com.littyl.digix.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.littyl.digix.BuildConfig
import com.littyl.digix.R
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.Requirement
import com.littyl.digix.core.Store
import com.littyl.digix.engine.HelperService
import com.littyl.digix.engine.ShizukuBridge
import com.littyl.digix.ui.ButtonKind
import com.littyl.digix.ui.DigixButton
import com.littyl.digix.ui.LargeTitle
import com.littyl.digix.ui.Readiness
import com.littyl.digix.ui.PinnedBar
import com.littyl.digix.ui.isTitleScrolledAway
import com.littyl.digix.ui.VSpace
import com.littyl.digix.ui.rememberResumeTick
import com.littyl.digix.ui.theme.Digix
import com.littyl.digix.ui.theme.DigixMotion
import com.littyl.digix.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

/** Walks a person through phone controls, one step at a time, showing what is already done. */
@Composable
fun SetupScreen(action: ActionType, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val tick = rememberResumeTick()
    val actionName = stringResource(action.title)

    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuReady by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        // Quick, gentle polling: permission answers arrive while this screen is visible.
        while (true) {
            shizukuRunning = ShizukuBridge.isInstalledAndRunning()
            shizukuReady = ShizukuBridge.isReady()
            delay(1000)
        }
    }

    val helperRunning by HelperService.running.collectAsStateWithLifecycle()
    val labels by Store.tileLabels.collectAsStateWithLifecycle()
    val learned by HelperService.learned.collectAsStateWithLifecycle()
    var teaching by remember { mutableStateOf(false) }
    var explainHelper by remember { mutableStateOf(false) }

    LaunchedEffect(learned) {
        if (learned == action) {
            teaching = false
            haptics.confirm()
        }
    }
    DisposableEffect(Unit) {
        onDispose { HelperService.cancelTeaching() }
    }

    val needsTile = action.requirement == Requirement.PHONE_CONTROL
    val offersShizuku = needsTile && shizukuRunning
    val hasLabel = labels.containsKey(action)
    var preferHelper by remember { mutableStateOf(false) }
    val offersDirect = action == ActionType.HOTSPOT && Readiness.canOfferDirectHotspot()
    val directReady = remember(tick, action) {
        when (action) {
            ActionType.WIFI -> Readiness.canSwitchWifiDirectly(context)
            ActionType.HOTSPOT -> Readiness.canSwitchHotspotDirectly(context)
            else -> false
        }
    }
    val showHelper = !needsTile || (!directReady && !offersDirect && !shizukuReady && (!offersShizuku || preferHelper))
    val finished = if (needsTile) directReady || shizukuReady || (helperRunning && hasLabel) else helperRunning

    if (explainHelper) {
        AlertDialog(
            onDismissRequest = { explainHelper = false },
            title = { Text(stringResource(R.string.setup_helper_title)) },
            text = { Text(stringResource(R.string.setup_helper_disclosure)) },
            confirmButton = {
                TextButton(onClick = {
                    explainHelper = false
                    context.startActivity(helperSettingsIntent(context.packageName, context))
                }) { Text(stringResource(R.string.setup_helper_agree)) }
            },
            dismissButton = {
                TextButton(onClick = { explainHelper = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    val scroll = rememberScrollState()
    val title = stringResource(R.string.setup_title, actionName)
    Column(Modifier.fillMaxSize()) {
    PinnedBar(title, collapsed = scroll.isTitleScrolledAway(), onBack = onBack)
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(scroll)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        LargeTitle(title)
        Column(Modifier.padding(horizontal = 16.dp)) {
            VSpace(6.dp)
            Text(
                stringResource(if (needsTile) R.string.setup_body else R.string.setup_lock_body),
                style = Digix.type.body,
                color = Digix.colors.secondary,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            VSpace(24.dp)

            if (offersDirect) {
                Step(
                    done = directReady,
                    title = stringResource(if (directReady) R.string.setup_direct_ready else R.string.setup_direct_title),
                    body = stringResource(R.string.setup_direct_body),
                    active = !directReady,
                ) {
                    DigixButton(
                        stringResource(R.string.setup_direct_cta),
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (offersShizuku && !directReady) {
                Step(
                    done = shizukuReady,
                    title = stringResource(if (shizukuReady) R.string.setup_shizuku_ready else R.string.setup_shizuku_title),
                    body = stringResource(R.string.setup_shizuku_body),
                    active = !shizukuReady,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DigixButton(
                            stringResource(R.string.setup_shizuku_cta),
                            onClick = { ShizukuBridge.requestPermission() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!preferHelper) {
                            DigixButton(
                                stringResource(R.string.setup_use_helper),
                                onClick = { preferHelper = true },
                                kind = ButtonKind.GHOST,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (showHelper) {
                Step(
                    done = helperRunning,
                    title = stringResource(if (helperRunning) R.string.setup_helper_ready else R.string.setup_helper_title),
                    body = stringResource(R.string.setup_helper_body),
                    active = !helperRunning,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DigixButton(
                            stringResource(R.string.setup_helper_cta),
                            onClick = {
                                if (BuildConfig.PLAY_EDITION) {
                                    explainHelper = true
                                } else {
                                    context.startActivity(helperSettingsIntent(context.packageName, context))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.setup_restricted_title),
                            style = Digix.type.subheadMedium,
                            color = Digix.colors.foreground,
                        )
                        Text(
                            stringResource(R.string.setup_restricted_body),
                            style = Digix.type.subhead,
                            color = Digix.colors.secondary,
                        )
                        DigixButton(
                            stringResource(R.string.setup_restricted_cta),
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            },
                            kind = ButtonKind.OUTLINE,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (needsTile) {
                    Step(
                        done = hasLabel,
                        title = if (hasLabel) stringResource(R.string.setup_teach_ready) else stringResource(R.string.setup_teach_title, actionName),
                        body = stringResource(R.string.setup_teach_body),
                        active = helperRunning && !hasLabel,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DigixButton(
                                stringResource(if (teaching) R.string.setup_teach_waiting else R.string.setup_teach_cta),
                                onClick = {
                                    teaching = HelperService.teach(action)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(R.string.setup_locked_note),
                                style = Digix.type.subhead,
                                color = Digix.colors.secondary,
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(visible = finished) {
                DigixButton(
                    stringResource(R.string.setup_done_cta),
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
    }
}

/** One setup step. Only the current step shows its details; finished steps collapse to a check. */
@Composable
private fun Step(
    done: Boolean,
    title: String,
    body: String,
    active: Boolean,
    actions: @Composable () -> Unit,
) {
    val colors = Digix.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 22.dp),
    ) {
        AnimatedContent(
            targetState = done,
            transitionSpec = {
                (scaleIn(DigixMotion.lively(), initialScale = 0.5f) + fadeIn()) togetherWith
                    (scaleOut(DigixMotion.quick()) + fadeOut())
            },
            label = "step mark",
        ) { complete ->
            if (complete) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            } else {
                Box(
                    Modifier
                        .size(24.dp)
                        .border(1.5.dp, if (active) colors.foreground else colors.border, CircleShape),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = Digix.type.bodyMedium,
                color = if (active || done) colors.foreground else colors.secondary,
            )
            AnimatedVisibility(
                visible = active,
                enter = fadeIn() + expandVertically(DigixMotion.smooth()),
                exit = fadeOut() + shrinkVertically(DigixMotion.smooth()),
            ) {
                Column {
                    VSpace(4.dp)
                    Text(body, style = Digix.type.subhead, color = colors.secondary)
                    VSpace(14.dp)
                    actions()
                }
            }
        }
    }
}

/** Opens Digix's own accessibility page where supported, which saves a search through the list. */
private fun helperSettingsIntent(packageName: String, context: android.content.Context): Intent {
    val component = ComponentName(context, HelperService::class.java).flattenToString()
    val extras = Bundle().apply { putString(":settings:fragment_args_key", component) }
    return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .putExtra(":settings:fragment_args_key", component)
        .putExtra(":settings:show_fragment_args", extras)
}
