package com.littyl.digix.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.littyl.digix.R
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.CodeBinding
import com.littyl.digix.core.HomeCommand
import com.littyl.digix.engine.GoogleHome
import com.littyl.digix.engine.HomeConnectResult
import com.littyl.digix.engine.HomeDeviceInfo
import com.littyl.digix.ui.ButtonKind
import com.littyl.digix.ui.Chevron
import com.littyl.digix.ui.DigixButton
import com.littyl.digix.ui.Group
import com.littyl.digix.ui.GroupDivider
import com.littyl.digix.ui.GroupRow
import com.littyl.digix.ui.InlineMessage
import com.littyl.digix.ui.SectionLabel
import com.littyl.digix.ui.VSpace
import com.littyl.digix.ui.theme.Digix
import com.littyl.digix.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

/** Connect once, then choose a device and what the code does to it. */
@Composable
fun GoogleHomeSection(
    deviceId: String,
    deviceName: String,
    command: HomeCommand,
    onDevice: (HomeDeviceInfo) -> Unit,
    onCommand: (HomeCommand) -> Unit,
) {
    val connected by GoogleHome.connected.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Bumped to look for devices again, after changing home or a failed attempt.
    var attempt by remember { mutableIntStateOf(0) }

    fun connect(changeHome: Boolean) {
        connecting = true
        failed = false
        scope.launch {
            val result = GoogleHome.connect(changeHome)
            connecting = false
            failed = result == HomeConnectResult.FAILED
            if (result == HomeConnectResult.CONNECTED) attempt++
        }
    }

    LaunchedEffect(Unit) { GoogleHome.refresh() }

    Column {
        if (connected) {
            DevicePicker(deviceId, deviceName, attempt, onDevice, onRetry = { attempt++ })
            VSpace(22.dp)
            CommandPicker(command, onCommand)
            VSpace(14.dp)
            DigixButton(
                stringResource(R.string.home_change),
                { connect(changeHome = true) },
                kind = ButtonKind.OUTLINE,
                enabled = !connecting,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Group {
                GroupRow(
                    title = stringResource(R.string.home_connect),
                    body = stringResource(if (connecting) R.string.home_connecting else R.string.home_connect_body),
                    icon = R.drawable.ic_house,
                    bodyColor = Digix.colors.warning,
                    onClick = { if (!connecting) connect(changeHome = false) },
                    trailing = { Chevron() },
                )
            }
        }
        InlineMessage(text = if (failed) stringResource(R.string.home_connect_failed) else null)
    }
}

@Composable
private fun DevicePicker(
    deviceId: String,
    deviceName: String,
    attempt: Int,
    onDevice: (HomeDeviceInfo) -> Unit,
    onRetry: () -> Unit,
) {
    val haptics = rememberHaptics()
    var loading by remember { mutableStateOf(true) }
    var devices by remember { mutableStateOf<List<HomeDeviceInfo>?>(null) }
    LaunchedEffect(attempt) {
        loading = true
        devices = GoogleHome.devices()
        loading = false
    }
    val found = devices
    val noRoom = stringResource(R.string.home_no_room)

    SectionLabel(stringResource(R.string.home_device))
    when {
        loading -> Text(stringResource(R.string.home_loading), style = Digix.type.subhead, color = Digix.colors.secondary)
        found == null -> {
            Text(stringResource(R.string.home_load_failed), style = Digix.type.subhead, color = Digix.colors.secondary)
            VSpace(8.dp)
            DigixButton(stringResource(R.string.home_retry), onRetry, kind = ButtonKind.OUTLINE, modifier = Modifier.fillMaxWidth())
        }
        found.isEmpty() && deviceId.isBlank() ->
            Text(stringResource(R.string.home_empty), style = Digix.type.subhead, color = Digix.colors.secondary)
        else -> Group {
            // A saved device stays visible after it disappears, so the code's problem can be seen.
            if (deviceId.isNotBlank() && found.none { it.id == deviceId }) {
                ChoiceRow(deviceName, stringResource(R.string.home_missing_device), selected = true) { }
                if (found.isNotEmpty()) GroupDivider()
            }
            found.forEachIndexed { index, device ->
                if (index > 0) GroupDivider()
                ChoiceRow(device.name, device.room ?: noRoom, selected = device.id == deviceId) {
                    haptics.press()
                    onDevice(device)
                }
            }
        }
    }
}

@Composable
private fun CommandPicker(command: HomeCommand, onCommand: (HomeCommand) -> Unit) {
    val haptics = rememberHaptics()
    SectionLabel(stringResource(R.string.home_command))
    Group {
        listOf(HomeCommand.TOGGLE, HomeCommand.ON, HomeCommand.OFF).forEachIndexed { index, option ->
            if (index > 0) GroupDivider()
            ChoiceRow(
                label = stringResource(
                    when (option) {
                        HomeCommand.TOGGLE -> R.string.home_command_toggle
                        HomeCommand.ON -> R.string.home_command_on
                        HomeCommand.OFF -> R.string.home_command_off
                    },
                ),
                body = if (option == HomeCommand.TOGGLE) stringResource(R.string.home_command_toggle_body) else null,
                selected = option == command,
            ) {
                haptics.press()
                onCommand(option)
            }
        }
    }
}

/** Several codes can use Google Home, so each is named for what it does. */
@Composable
fun googleHomeTitle(binding: CodeBinding): String? {
    if (binding.action != ActionType.GOOGLE_HOME) return null
    val name = binding.homeDeviceName ?: return null
    return stringResource(
        when (binding.homeCommand) {
            HomeCommand.ON -> R.string.home_title_on
            HomeCommand.OFF -> R.string.home_title_off
            else -> R.string.home_title_toggle
        },
        name,
    )
}
