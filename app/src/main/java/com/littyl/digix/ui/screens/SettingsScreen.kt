package com.littyl.digix.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.littyl.digix.R
import com.littyl.digix.ui.Wordmark
import com.littyl.digix.ui.Halo
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.littyl.digix.core.AssistantConfig
import com.littyl.digix.core.Store
import com.littyl.digix.engine.HelperService
import com.littyl.digix.engine.Notifications
import com.littyl.digix.engine.ShizukuBridge
import com.littyl.digix.ui.Chevron
import com.littyl.digix.ui.CheckedLine
import com.littyl.digix.ui.DigixSwitch
import com.littyl.digix.ui.Group
import com.littyl.digix.ui.GroupDivider
import com.littyl.digix.ui.GroupRow
import com.littyl.digix.ui.LargeTitle
import com.littyl.digix.ui.PinnedBar
import com.littyl.digix.ui.Readiness
import com.littyl.digix.ui.SectionLabel
import com.littyl.digix.ui.VSpace
import com.littyl.digix.ui.isTitleScrolledAway
import com.littyl.digix.ui.rememberResumeTick
import com.littyl.digix.ui.theme.Digix

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenHelper: () -> Unit, onOpenAssistant: () -> Unit) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val haptics by Store.haptics.collectAsStateWithLifecycle()
    val spoken by Store.spokenReplies.collectAsStateWithLifecycle()
    val helper by HelperService.running.collectAsStateWithLifecycle()
    val shizukuBody = remember(tick) {
        when {
            ShizukuBridge.isReady() -> R.string.settings_shizuku_ready
            ShizukuBridge.isInstalledAndRunning() -> R.string.settings_shizuku_waiting
            else -> R.string.settings_shizuku_none
        }
    }
    val background = remember(tick) { Readiness.canStartInBackground(context) }
    val notifications = remember(tick) { Notifications.canPost(context) }
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val scroll = rememberScrollState()
    val title = stringResource(R.string.settings_title)
    val assistantProvider = remember(tick) { AssistantConfig.load(context)?.provider?.label }
    val assistantBody = assistantProvider?.let { stringResource(R.string.settings_assistant_on, it) }
        ?: stringResource(R.string.settings_assistant_off)
    val warning = Digix.colors.warning
    val secondary = Digix.colors.secondary

    Column(Modifier.fillMaxSize()) {
        PinnedBar(title, collapsed = scroll.isTitleScrolledAway(), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            LargeTitle(title)
            Column(Modifier.padding(horizontal = 16.dp)) {
                VSpace(18.dp)
                SectionLabel(stringResource(R.string.settings_section_general))
                Group {
                    GroupRow(
                        title = stringResource(R.string.settings_spoken),
                        body = stringResource(R.string.settings_spoken_body),
                        trailing = {
                            DigixSwitch(spoken, { Store.setSpokenReplies(it) }, description = stringResource(R.string.settings_spoken))
                        },
                    )
                    GroupDivider()
                    GroupRow(
                        title = stringResource(R.string.settings_assistant),
                        body = assistantBody,
                        onClick = onOpenAssistant,
                        trailing = { Chevron() },
                    )
                    GroupDivider()
                    GroupRow(
                        title = stringResource(R.string.settings_haptics),
                        body = stringResource(R.string.settings_haptics_body),
                        trailing = {
                            DigixSwitch(haptics, { Store.setHaptics(it) }, description = stringResource(R.string.settings_haptics))
                        },
                    )
                    GroupDivider()
                    GroupRow(
                        title = stringResource(R.string.settings_background),
                        body = stringResource(if (background) R.string.settings_background_on else R.string.settings_background_off),
                        bodyColor = if (background) secondary else warning,
                        onClick = if (background) null else ({ context.startActivity(Readiness.backgroundStartIntent(context)) }),
                        trailing = if (background) null else ({ Chevron() }),
                    )
                    GroupDivider()
                    GroupRow(
                        title = stringResource(R.string.settings_notifications),
                        body = stringResource(if (notifications) R.string.settings_notifications_on else R.string.settings_notifications_off),
                        bodyColor = if (notifications) secondary else warning,
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                        trailing = { Chevron() },
                    )
                    GroupDivider()
                    GroupRow(
                        title = stringResource(R.string.settings_helpers),
                        body = stringResource(if (helper) R.string.settings_helper_on else R.string.settings_helper_off),
                        onClick = onOpenHelper,
                        trailing = { Chevron() },
                    )
                    GroupDivider()
                    GroupRow(
                        title = stringResource(R.string.settings_shizuku),
                        body = stringResource(shizukuBody),
                    )
                }
                VSpace(26.dp)
                SectionLabel(stringResource(R.string.settings_section_trust))
                VSpace(6.dp)
                CheckedLine(stringResource(R.string.settings_trust_calls))
                VSpace(12.dp)
                CheckedLine(stringResource(R.string.settings_trust_reply))
                VSpace(12.dp)
                CheckedLine(stringResource(R.string.settings_trust_emergency))
                VSpace(12.dp)
                CheckedLine(stringResource(R.string.settings_trust_battery))
                VSpace(12.dp)
                CheckedLine(stringResource(R.string.settings_trust_remove))
                VSpace(26.dp)
                SectionLabel(stringResource(R.string.settings_section_about))
                Group {
                    GroupRow(
                        title = stringResource(R.string.settings_replay),
                        onClick = { Store.setOnboarded(false) },
                        trailing = { Chevron() },
                    )
                }
                VSpace(12.dp)
                Text(
                    stringResource(R.string.settings_tile_hint),
                    style = Digix.type.subhead,
                    color = secondary,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                VSpace(12.dp)
                Halo(
                    clearCentre = 0.4f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Wordmark(markSize = 32.dp)
                        VSpace(6.dp)
                        Text(
                            stringResource(R.string.settings_version, version),
                            style = Digix.type.subhead,
                            color = secondary,
                        )
                    }
                }
                VSpace(24.dp)
            }
        }
    }
}
