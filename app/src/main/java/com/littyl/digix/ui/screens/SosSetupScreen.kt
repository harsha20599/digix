package com.littyl.digix.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.littyl.digix.BuildConfig
import com.littyl.digix.R
import com.littyl.digix.core.SosConfig
import com.littyl.digix.core.SosContact
import com.littyl.digix.core.SosSettings
import com.littyl.digix.engine.LocationTracker
import com.littyl.digix.engine.SosService
import com.littyl.digix.ui.ButtonKind
import com.littyl.digix.ui.Chevron
import com.littyl.digix.ui.DigixButton
import com.littyl.digix.ui.DigixSwitch
import com.littyl.digix.ui.Group
import com.littyl.digix.ui.GroupDivider
import com.littyl.digix.ui.GroupRow
import com.littyl.digix.ui.IconAction
import com.littyl.digix.ui.InlineMessage
import com.littyl.digix.ui.LargeTitle
import com.littyl.digix.ui.PinnedBar
import com.littyl.digix.ui.Readiness
import com.littyl.digix.ui.SectionLabel
import com.littyl.digix.ui.VSpace
import com.littyl.digix.ui.isTitleScrolledAway
import com.littyl.digix.ui.rememberResumeTick
import com.littyl.digix.ui.theme.Digix

private const val MAX_CONTACTS = 5

/** Sets up SOS, and shows plainly what would stop it from working, before it's ever needed. */
@Composable
fun SosSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val saved by SosConfig.settings.collectAsStateWithLifecycle()
    val test by SosService.lastTestResult.collectAsStateWithLifecycle()
    var message by rememberSaveable { mutableStateOf(saved.message) }
    var testing by remember { mutableStateOf(false) }
    var phoneDraft by rememberSaveable { mutableStateOf("") }
    val tick = rememberResumeTick()
    var refresh by remember { mutableStateOf(0) }

    val canText = remember(tick, refresh) { Readiness.canSendSms(context) }
    val canLocate = remember(tick, refresh) { LocationTracker.hasPermission(context) }
    val locationOn = remember(tick) { LocationTracker.isEnabled(context) }
    val unrestricted = remember(tick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    var permissionsAsked by rememberSaveable { mutableStateOf(false) }

    fun update(settings: SosSettings) = SosConfig.save(context, settings)

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsAsked = true
        refresh++
    }
    val picker = pickContact { contact ->
        if (saved.contacts.size < MAX_CONTACTS && saved.contacts.none { it.phone == contact.phone }) {
            update(saved.copy(contacts = saved.contacts + contact))
        }
    }

    val scroll = rememberScrollState()
    val title = stringResource(R.string.action_sos)
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
                Text(stringResource(R.string.sos_body), style = Digix.type.body, color = Digix.colors.secondary)
                VSpace(22.dp)

                SectionLabel(stringResource(R.string.sos_contacts))
                if (saved.contacts.isNotEmpty()) {
                    Group {
                        saved.contacts.forEachIndexed { index, contact ->
                            if (index > 0) GroupDivider()
                            GroupRow(
                                title = contact.name,
                                body = contact.phone,
                                trailing = {
                                    IconAction(
                                        R.drawable.ic_trash_2,
                                        stringResource(R.string.sos_remove_contact, contact.name),
                                        { update(saved.copy(contacts = saved.contacts - contact)) },
                                    )
                                },
                            )
                        }
                    }
                    VSpace(10.dp)
                }
                if (saved.contacts.size < MAX_CONTACTS) {
                    DigixButton(stringResource(R.string.contact_choose), picker, kind = ButtonKind.OUTLINE, modifier = Modifier.fillMaxWidth())
                    VSpace(10.dp)
                    TextEntry(
                        label = stringResource(R.string.contact_or_type),
                        value = phoneDraft,
                        hint = "+91 98765 43210",
                        phone = true,
                        onChange = { phoneDraft = it },
                    )
                    if (phoneDraft.count { it.isDigit() } >= MIN_DIGITS) {
                        VSpace(8.dp)
                        DigixButton(
                            stringResource(R.string.action_add),
                            {
                                update(saved.copy(contacts = saved.contacts + SosContact(phoneDraft.trim(), phoneDraft.trim())))
                                phoneDraft = ""
                            },
                            kind = ButtonKind.OUTLINE,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                VSpace(22.dp)
                TextEntry(
                    label = stringResource(R.string.sos_message_label),
                    value = message,
                    hint = stringResource(R.string.sos_default_message),
                    singleLine = false,
                    onChange = {
                        message = it
                        update(saved.copy(message = it))
                    },
                )

                VSpace(22.dp)
                SectionLabel(stringResource(R.string.sos_checks))
                Group {
                    val warning = Digix.colors.warning
                    val secondary = Digix.colors.secondary
                    GroupRow(
                        title = stringResource(R.string.sos_check_sms),
                        body = stringResource(if (canText) R.string.sos_allowed else R.string.sos_tap_to_allow),
                        bodyColor = if (canText) secondary else warning,
                        onClick = if (canText) null else ({ permissions.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)) }),
                        trailing = if (canText) null else ({ Chevron() }),
                    )
                    GroupDivider()
                    GroupRow(
                        title = stringResource(R.string.sos_check_location),
                        body = stringResource(if (canLocate) R.string.sos_allowed else R.string.sos_tap_to_allow),
                        bodyColor = if (canLocate) secondary else warning,
                        onClick = if (canLocate) {
                            null
                        } else {
                            { permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                        },
                        trailing = if (canLocate) null else ({ Chevron() }),
                    )
                    if (BuildConfig.PLAY_EDITION && canLocate) {
                        GroupDivider()
                        BackgroundLocationPermissionRow(R.string.sos_background_location_body)
                    }
                    GroupDivider()
                    GroupRow(
                        title = stringResource(R.string.sos_check_location_on),
                        body = stringResource(if (locationOn) R.string.sos_check_on else R.string.sos_check_location_off),
                        bodyColor = if (locationOn) secondary else warning,
                        onClick = if (locationOn) null else ({ context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }),
                        trailing = if (locationOn) null else ({ Chevron() }),
                    )
                    GroupDivider()
                    GroupRow(
                        title = stringResource(R.string.sos_check_battery),
                        body = stringResource(if (unrestricted) R.string.sos_allowed else R.string.sos_check_battery_off),
                        bodyColor = if (unrestricted) secondary else warning,
                        onClick = if (unrestricted) null else ({ context.startActivity(Readiness.backgroundStartIntent(context)) }),
                        trailing = if (unrestricted) null else ({ Chevron() }),
                    )
                    GroupDivider()
                    GroupRow(
                        title = stringResource(R.string.sos_call_first),
                        body = stringResource(R.string.sos_call_first_body),
                        trailing = {
                            DigixSwitch(
                                saved.callFirstContact,
                                { on ->
                                    update(saved.copy(callFirstContact = on))
                                    if (on) permissions.launch(arrayOf(Manifest.permission.CALL_PHONE))
                                },
                                description = stringResource(R.string.sos_call_first),
                            )
                        },
                    )
                }
                InlineMessage(
                    text = if (permissionsAsked && !(canText && canLocate)) stringResource(R.string.sos_restricted) else null,
                )
                if (permissionsAsked && !(canText && canLocate)) {
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
                VSpace(18.dp)
                Text(stringResource(R.string.sos_how), style = Digix.type.subhead, color = Digix.colors.secondary)
                VSpace(10.dp)
                Text(stringResource(R.string.sos_limits), style = Digix.type.subheadMedium, color = Digix.colors.foreground)
                VSpace(16.dp)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val result = test
            InlineMessage(
                text = when {
                    result == null -> null
                    result.sent == result.total && result.hadLocation -> stringResource(R.string.sos_test_good, result.sent)
                    result.sent == result.total -> stringResource(R.string.sos_test_no_fix, result.sent)
                    else -> stringResource(R.string.sos_test_bad, result.sent, result.total)
                },
                color = if (result != null && result.sent == result.total && result.hadLocation) Digix.colors.foreground else Digix.colors.destructive,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DigixButton(
                    stringResource(R.string.sos_test),
                    {
                        testing = true
                        SosService.sendTest(context)
                    },
                    enabled = saved.contacts.isNotEmpty() && canText,
                    kind = ButtonKind.OUTLINE,
                    modifier = Modifier.weight(1f),
                )
                DigixButton(stringResource(R.string.action_done), onBack, modifier = Modifier.weight(1f))
            }
        }
    }
}

private const val MIN_DIGITS = 5

/** Android's own contact picker: Digix sees only the one number chosen, not the address book. */
@Composable
fun pickContact(onPicked: (SosContact) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { row ->
                if (row.moveToFirst()) onPicked(SosContact(row.getString(1) ?: row.getString(0), row.getString(0)))
            }
        }
    }
    return {
        launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
    }
}
