package com.littyl.digix.ui

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.littyl.digix.core.AssistantConfig
import com.littyl.digix.core.CodeBinding
import com.littyl.digix.core.Requirement
import com.littyl.digix.core.SosConfig
import com.littyl.digix.engine.GoogleHome
import com.littyl.digix.engine.HelperService
import com.littyl.digix.engine.LocationTracker
import com.littyl.digix.engine.PhoneState
import com.littyl.digix.engine.ShizukuBridge

/** Answers one question for the interface: can this code run on this phone right now? */
object Readiness {
    fun holdsCallRole(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)

    /** From Android 12, long-running work can't start from the background without this exemption. */
    fun canStartInBackground(context: Context): Boolean =
        context.applicationInfo.targetSdkVersion < Build.VERSION_CODES.S ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun backgroundStartIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Android 16 refuses this route. Below that it only needs "Modify system settings". */
    fun canOfferDirectHotspot(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA

    fun canSwitchHotspotDirectly(context: Context): Boolean =
        canOfferDirectHotspot() && Settings.System.canWrite(context)

    /** Phone access too, because choosing a SIM on a dual-SIM phone requires it. */
    fun canSendSms(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
            PhoneState.canRead(context)

    fun hasMicrophone(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun isReady(context: Context, binding: CodeBinding): Boolean = when (binding.action.requirement) {
        Requirement.NONE -> true
        Requirement.BACKGROUND_START -> canStartInBackground(context)
        Requirement.DND_ACCESS -> context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        Requirement.WEB_ADDRESS -> !binding.webAddress.isNullOrBlank()
        Requirement.PHONE_CONTROL ->
            canSwitchHotspotDirectly(context) || ShizukuBridge.isReady() || HelperService.isConfigured(binding.action)
        Requirement.ASSISTANT -> AssistantConfig.load(context) != null && hasMicrophone(context)
        Requirement.CONTACT -> !binding.phone.isNullOrBlank()
        Requirement.SMS -> !binding.phone.isNullOrBlank() && !binding.message.isNullOrBlank() && canSendSms(context)
        Requirement.SMS_LOCATION -> !binding.phone.isNullOrBlank() && canSendSms(context) && LocationTracker.hasPermission(context)
        Requirement.SOS -> SosConfig.settings.value.contacts.isNotEmpty() && canSendSms(context) && LocationTracker.hasPermission(context)
        Requirement.WHATSAPP -> !binding.phone.isNullOrBlank() && !binding.message.isNullOrBlank() && HelperService.isRunning
        Requirement.HELPER -> HelperService.isRunning
        Requirement.GOOGLE_HOME -> GoogleHome.connected.value && !binding.homeDeviceId.isNullOrBlank()
    }
}

/**
 * Settings change outside the app, so anything reading them must look again whenever a person
 * returns. Read this value inside a composable to refresh on resume.
 */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    return tick
}
