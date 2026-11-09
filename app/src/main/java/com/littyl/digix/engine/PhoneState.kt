package com.littyl.digix.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** Tells whether a real call is going on. Digix must never disturb one. */
object PhoneState {
    /** Android 12 and later require permission to read call state. */
    fun canRead(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /**
     * True when a call is active or ringing on any SIM, or a call app has the line. Reads each SIM's
     * own state, because the phone-wide state also counts the code that is being dialed right now.
     * Check [canRead] first. The caller also guards against the system refusing.
     */
    @SuppressLint("MissingPermission")
    fun isBusy(context: Context): Boolean {
        val mode = context.getSystemService(AudioManager::class.java).mode
        if (mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE) return true
        val telephony = context.getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            return telephony.callState != TelephonyManager.CALL_STATE_IDLE
        }
        val subscriptions = context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
        return subscriptions.any { subscription ->
            telephony.createForSubscriptionId(subscription.subscriptionId).callStateForSubscription != TelephonyManager.CALL_STATE_IDLE
        }
    }
}
