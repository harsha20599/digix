package com.littyl.digix.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.littyl.digix.R
import com.littyl.digix.ui.Chevron
import com.littyl.digix.ui.GroupRow
import com.littyl.digix.ui.Readiness
import com.littyl.digix.ui.rememberResumeTick
import com.littyl.digix.ui.theme.Digix

@Composable
internal fun BackgroundLocationPermissionRow(@StringRes disclosure: Int) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val allowed = remember(tick) { Readiness.canLocateInBackground(context) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }

    GroupRow(
        title = stringResource(R.string.sos_background_location),
        body = stringResource(if (allowed) R.string.sos_allowed else R.string.sos_tap_to_allow),
        bodyColor = if (allowed) Digix.colors.secondary else Digix.colors.warning,
        onClick = if (allowed) null else ({ showDisclosure = true }),
        trailing = if (allowed) null else ({ Chevron() }),
    )
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.sos_background_location)) },
            text = { Text(stringResource(disclosure)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    context.startActivity(Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ))
                }) { Text(stringResource(R.string.setup_helper_cta)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text(stringResource(R.string.action_not_now)) }
            },
        )
    }
}
