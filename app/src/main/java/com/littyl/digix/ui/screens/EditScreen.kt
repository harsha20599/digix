package com.littyl.digix.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.littyl.digix.R
import com.littyl.digix.ui.Halo
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateDpAsState
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.CodeBinding
import com.littyl.digix.core.CodeRules
import com.littyl.digix.core.HomeCommand
import com.littyl.digix.core.Requirement
import com.littyl.digix.core.RunStatus
import com.littyl.digix.core.Store
import com.littyl.digix.engine.ActionService
import com.littyl.digix.ui.ButtonKind
import com.littyl.digix.ui.Chevron
import com.littyl.digix.ui.DigixButton
import com.littyl.digix.ui.Group
import com.littyl.digix.ui.GroupRow
import com.littyl.digix.ui.InlineMessage
import com.littyl.digix.ui.LargeTitle
import com.littyl.digix.ui.PinnedBar
import com.littyl.digix.ui.Readiness
import com.littyl.digix.ui.VSpace
import com.littyl.digix.ui.isTitleScrolledAway
import com.littyl.digix.ui.rememberResumeTick
import com.littyl.digix.ui.theme.Digix
import com.littyl.digix.ui.theme.DigixMotion
import com.littyl.digix.ui.theme.DigixShape
import com.littyl.digix.ui.theme.rememberHaptics

@Composable
fun EditScreen(
    existingCode: String?,
    newAction: ActionType?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onSetup: (ActionType) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val colors = Digix.colors
    val codes by Store.codes.collectAsStateWithLifecycle()

    // Captured once: saving or deleting changes the store while this screen is still animating out.
    val original = remember(existingCode) { existingCode?.let(Store::find) }
    val action = newAction ?: original?.action
    if (action == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var savedCode by rememberSaveable { mutableStateOf(original?.code) }
    var code by rememberSaveable { mutableStateOf(original?.code ?: CodeRules.suggest(codes)) }
    var webAddress by rememberSaveable { mutableStateOf(original?.webAddress.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(original?.phone.orEmpty()) }
    var contactName by rememberSaveable { mutableStateOf(original?.contactName.orEmpty()) }
    var message by rememberSaveable { mutableStateOf(original?.message.orEmpty()) }
    var homeDeviceId by rememberSaveable { mutableStateOf(original?.homeDeviceId.orEmpty()) }
    var homeDeviceName by rememberSaveable { mutableStateOf(original?.homeDeviceName.orEmpty()) }
    var homeCommand by rememberSaveable { mutableStateOf(original?.homeCommand ?: HomeCommand.TOGGLE) }
    var permissionsAsked by rememberSaveable { mutableStateOf(false) }
    var permissionTick by remember { mutableStateOf(0) }
    val needsContact = action.requirement in CONTACT_REQUIREMENTS
    val isHome = action.requirement == Requirement.GOOGLE_HOME
    val needsMessage = action.requirement == Requirement.SMS || action.requirement == Requirement.WHATSAPP
    val takesMessage = needsMessage || action.requirement == Requirement.SMS_LOCATION
    val picker = pickContact { contact ->
        phone = contact.phone
        contactName = contact.name
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsAsked = true
        permissionTick++
    }
    var runResult by remember { mutableStateOf<String?>(null) }
    var runFailed by remember { mutableStateOf(false) }

    val problem = CodeRules.problem(context, code, codes, ignoring = savedCode)
    val webNeeded = action.requirement == Requirement.WEB_ADDRESS
    val webValid = !webNeeded || webAddress.startsWith("http://") || webAddress.startsWith("https://")
    val contactValid = !needsContact || phone.count { it.isDigit() } >= MIN_PHONE_DIGITS
    val messageValid = !needsMessage || message.isNotBlank()
    val canSave = problem == null && webValid && contactValid && messageValid && (!isHome || homeDeviceId.isNotBlank())

    val tick = rememberResumeTick()
    val draft = CodeBinding(
        code,
        action,
        webAddress.takeIf { webNeeded && it.isNotBlank() },
        phone.trim().takeIf { needsContact && it.isNotBlank() },
        contactName.takeIf { needsContact && it.isNotBlank() },
        message.takeIf { takesMessage && it.isNotBlank() },
        homeDeviceId.takeIf { isHome && it.isNotBlank() },
        homeDeviceName.takeIf { isHome && it.isNotBlank() },
        homeCommand.takeIf { isHome },
    )
    val ready = remember(tick, draft, permissionTick) { Readiness.isReady(context, draft) }

    LaunchedEffect(Unit) {
        Store.entries.collect { entry ->
            if (entry.code == savedCode) {
                runResult = entry.message
                runFailed = entry.status != RunStatus.DONE
                if (runFailed) haptics.reject() else haptics.confirm()
            }
        }
    }

    val scroll = rememberScrollState()
    val title = stringResource(action.title)

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        PinnedBar(title, collapsed = scroll.isTitleScrolledAway(), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll),
        ) {
            LargeTitle(title)
            Column(Modifier.padding(horizontal = 16.dp)) {
                VSpace(6.dp)
                Text(stringResource(action.summary), style = Digix.type.body, color = colors.secondary)
                VSpace(24.dp)
                CodeField(
                    value = code,
                    hasError = problem != null,
                    label = stringResource(R.string.edit_code_label),
                ) {
                    code = CodeRules.normalize(it).take(CodeRules.MAX_LENGTH)
                }
                VSpace(4.dp)
                InlineMessage(
                    text = problem?.let { stringResource(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (problem == null) {
                    Text(
                        stringResource(R.string.edit_code_hint),
                        style = Digix.type.subhead,
                        color = colors.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
                if (webNeeded) {
                    VSpace(24.dp)
                    Text(stringResource(R.string.edit_web_label), style = Digix.type.subheadMedium, color = colors.foreground)
                    VSpace(6.dp)
                    WebField(webAddress, stringResource(R.string.edit_web_label)) { webAddress = it.trim() }
                    InlineMessage(
                        text = if (webAddress.isNotEmpty() && !webValid) stringResource(R.string.edit_web_error) else null,
                    )
                }
                if (needsContact) {
                    VSpace(24.dp)
                    Text(stringResource(R.string.contact_label), style = Digix.type.subheadMedium, color = colors.foreground)
                    if (contactName.isNotBlank()) {
                        VSpace(4.dp)
                        Text(contactName, style = Digix.type.body, color = colors.secondary)
                    }
                    VSpace(8.dp)
                    DigixButton(stringResource(R.string.contact_choose), picker, kind = ButtonKind.OUTLINE, modifier = Modifier.fillMaxWidth())
                    VSpace(10.dp)
                    TextEntry(
                        label = stringResource(R.string.contact_or_type),
                        value = phone,
                        hint = "+91 98765 43210",
                        phone = true,
                        onChange = {
                            phone = it
                            contactName = ""
                        },
                    )
                }
                if (takesMessage) {
                    VSpace(20.dp)
                    TextEntry(
                        label = stringResource(R.string.message_label),
                        value = message,
                        hint = if (needsMessage) "" else stringResource(R.string.message_location_hint),
                        singleLine = false,
                        onChange = { message = it },
                    )
                }
                if (action.requirement == Requirement.WHATSAPP) {
                    VSpace(14.dp)
                    Text(stringResource(R.string.edit_whatsapp_note), style = Digix.type.subhead, color = colors.secondary)
                }
                if (isHome) {
                    VSpace(24.dp)
                    GoogleHomeSection(
                        deviceId = homeDeviceId,
                        deviceName = homeDeviceName,
                        command = homeCommand,
                        onDevice = { device ->
                            homeDeviceId = device.id
                            homeDeviceName = device.name
                        },
                        onCommand = { homeCommand = it },
                    )
                }
                val missing = missingPermissions(context, action.requirement)
                if (missing.isNotEmpty()) {
                    VSpace(24.dp)
                    Group {
                        GroupRow(
                            title = stringResource(R.string.edit_permission_row),
                            body = stringResource(R.string.edit_permission_row_body),
                            icon = R.drawable.ic_shield_check,
                            bodyColor = colors.warning,
                            onClick = { permissions.launch(missing.toTypedArray()) },
                            trailing = { Chevron() },
                        )
                    }
                    if (permissionsAsked) {
                        InlineMessage(text = stringResource(R.string.edit_restricted))
                        VSpace(8.dp)
                        DigixButton(
                            stringResource(R.string.setup_restricted_cta),
                            {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            },
                            kind = ButtonKind.OUTLINE,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else if (!ready && action.requirement !in NO_SETUP_ROW) {
                    VSpace(24.dp)
                    Group { SetupRow(action, onSetup) }
                }
                InlineMessage(
                    text = runResult,
                    color = if (runFailed) colors.destructive else colors.foreground,
                    style = Digix.type.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
                val saved = savedCode
                if (saved != null) {
                    VSpace(28.dp)
                    Text(
                        stringResource(R.string.edit_delete_warning, saved),
                        style = Digix.type.subhead,
                        color = colors.secondary,
                    )
                    VSpace(10.dp)
                    DigixButton(
                        stringResource(R.string.action_delete),
                        onClick = {
                            haptics.reject()
                            Store.delete(saved)
                            onDone()
                        },
                        kind = ButtonKind.DESTRUCTIVE,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                VSpace(16.dp)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // A real SOS texts real people and a call is a call, so neither has a casual Run button.
            if (savedCode != null && action != ActionType.SOS && action != ActionType.CALL_CONTACT) {
                DigixButton(
                    stringResource(R.string.action_run_now),
                    onClick = {
                        // Runs what is on screen, so a person can test a change before leaving.
                        Store.save(draft, previousCode = savedCode)
                        savedCode = draft.code
                        runResult = null
                        ActionService.dispatch(context, draft)
                    },
                    kind = ButtonKind.OUTLINE,
                    enabled = canSave && ready,
                    modifier = Modifier.weight(1f),
                )
            }
            DigixButton(
                stringResource(R.string.action_save),
                onClick = {
                    Store.save(draft, previousCode = savedCode)
                    haptics.confirm()
                    onDone()
                },
                enabled = canSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val MIN_PHONE_DIGITS = 5
private val CONTACT_REQUIREMENTS = setOf(Requirement.CONTACT, Requirement.SMS, Requirement.SMS_LOCATION, Requirement.WHATSAPP)
private val NO_SETUP_ROW = setOf(Requirement.WEB_ADDRESS, Requirement.CONTACT, Requirement.SMS, Requirement.SMS_LOCATION, Requirement.GOOGLE_HOME)

private fun missingPermissions(context: Context, requirement: Requirement): List<String> {
    val wanted = when (requirement) {
        Requirement.SMS -> listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)
        Requirement.SMS_LOCATION -> listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        else -> emptyList()
    }
    return wanted.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
}

/** A labeled text field in the app's one field style. */
@Composable
fun TextEntry(
    label: String,
    value: String,
    hint: String = "",
    phone: Boolean = false,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    val colors = Digix.colors
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        targetValue = if (focused) colors.foreground else colors.border,
        animationSpec = DigixMotion.smooth(),
        label = "entry border",
    )
    val shape = RoundedCornerShape(DigixShape.radius)
    Text(label, style = Digix.type.subheadMedium, color = colors.foreground)
    VSpace(6.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Text),
        textStyle = Digix.type.body.copy(color = colors.foreground),
        cursorBrush = SolidColor(colors.foreground),
        decorationBox = { inner ->
            if (value.isEmpty() && hint.isNotEmpty()) {
                Text(hint, style = Digix.type.body, color = colors.secondary)
            }
            inner()
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .onFocusChanged { focused = it.isFocused }
            .border(DigixShape.hairline, border, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    )
}

@Composable
private fun SetupRow(action: ActionType, onSetup: (ActionType) -> Unit) {
    val context = LocalContext.current
    val warning = Digix.colors.warning
    when (action.requirement) {
        Requirement.BACKGROUND_START -> GroupRow(
            title = stringResource(R.string.edit_background_row),
            body = stringResource(R.string.edit_background_row_body),
            icon = R.drawable.ic_zap,
            bodyColor = warning,
            onClick = { context.startActivity(Readiness.backgroundStartIntent(context)) },
            trailing = { Chevron() },
        )
        Requirement.DND_ACCESS -> GroupRow(
            title = stringResource(R.string.edit_dnd_row),
            body = stringResource(R.string.edit_dnd_row_body),
            icon = R.drawable.ic_moon,
            bodyColor = warning,
            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
            trailing = { Chevron() },
        )
        else -> GroupRow(
            title = stringResource(R.string.edit_setup_row),
            body = stringResource(R.string.edit_setup_row_body),
            icon = R.drawable.ic_circle_alert,
            bodyColor = warning,
            onClick = { onSetup(action) },
            trailing = { Chevron() },
        )
    }
}

@Composable
private fun CodeField(value: String, hasError: Boolean, label: String, onChange: (String) -> Unit) {
    val colors = Digix.colors
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        targetValue = when {
            hasError -> colors.destructive
            focused -> colors.foreground
            else -> colors.border
        },
        animationSpec = DigixMotion.smooth(),
        label = "field border",
    )
    val underline by animateDpAsState(
        targetValue = if (focused || hasError) 2.dp else 1.dp,
        animationSpec = DigixMotion.quick(),
        label = "field underline",
    )
    // The code is the one big thing on this screen, so it sits on the halo, in dot type.
    Halo(
        clearCentre = 0.42f,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
    ) {
        // Codes are numbers on a dial pad, so they stay left to right in every language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = Digix.type.codeLarge.copy(color = colors.foreground, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(colors.live),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label }
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { field ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        field()
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .size(width = 132.dp, height = underline)
                                .background(border),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun WebField(value: String, label: String, onChange: (String) -> Unit) {
    val colors = Digix.colors
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        targetValue = if (focused) colors.foreground else colors.border,
        animationSpec = DigixMotion.smooth(),
        label = "web border",
    )
    val shape = RoundedCornerShape(DigixShape.radius)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        textStyle = Digix.type.body.copy(color = colors.foreground),
        cursorBrush = SolidColor(colors.foreground),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(stringResource(R.string.edit_web_hint), style = Digix.type.body, color = colors.secondary)
            }
            inner()
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .onFocusChanged { focused = it.isFocused }
            .border(DigixShape.hairline, border, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    )
}
