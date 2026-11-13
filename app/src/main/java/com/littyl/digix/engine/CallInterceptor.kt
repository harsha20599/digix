package com.littyl.digix.engine

import android.net.Uri
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import com.littyl.digix.R
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.RunStatus
import com.littyl.digix.core.CodeBinding
import com.littyl.digix.core.Store

/**
 * Sees every outgoing call before it is placed.
 *
 * Two rules matter here more than anywhere else in the app:
 * 1. A number that is not one of the person's codes passes through untouched, whatever goes wrong.
 * 2. A code is always stopped, even while Digix is switched off, so it can never become a real call.
 *
 * Dialed numbers that are not codes are never stored, logged or sent anywhere.
 */
open class CallInterceptor : CallRedirectionService() {

    override fun onPlaceCall(handle: Uri, initialPhoneAccount: PhoneAccountHandle, allowInteractiveResponse: Boolean) {
        val binding = findCode(handle)
        if (binding == null) {
            placeCallUnmodified()
            return
        }
        // SOS comes before everything else, and even the master switch can't stop it. The call that
        // follows is silent and exists only so the watch doesn't sit in a failed dial.
        if (binding.action == ActionType.SOS) {
            try {
                SosService.toggle(applicationContext, binding)
            } catch (e: SecurityException) {
                reportSosUnavailable(binding, e)
            } catch (e: IllegalStateException) {
                reportSosUnavailable(binding, e)
            } finally {
                answer(handle, binding)
            }
            return
        }
        // Speed dial: the dial simply becomes a real call to the chosen person.
        if (binding.action == ActionType.CALL_CONTACT && Store.enabled.value && !binding.phone.isNullOrBlank()) {
            redirectCall(Uri.fromParts("tel", binding.phone, null), initialPhoneAccount, false)
            ActionService.report(
                applicationContext,
                binding,
                ActionOutcome(RunStatus.DONE, getString(R.string.result_calling, binding.contactName ?: binding.phone)),
            )
            return
        }
        // A conversation needs the call whatever the spoken replies setting says.
        if (Store.spokenReplies.value || binding.action == ActionType.ASSISTANT) {
            answer(handle, binding)
            return
        }
        // The action starts first, because once cancelled Android may stop keeping this process alive.
        // Stopping the real call must still happen whatever goes wrong.
        try {
            ActionService.dispatch(applicationContext, binding)
        } finally {
            cancelCall()
        }
    }

    private fun reportSosUnavailable(binding: CodeBinding, error: RuntimeException) {
        Log.w(TAG, "SOS could not start", error)
        SosService.reportStartFailure(applicationContext, binding.code)
    }

    private fun answer(handle: Uri, binding: CodeBinding) {
        val canRedirect = try {
            PhoneState.canRead(this) && !PhoneState.isBusy(this)
        } catch (_: RuntimeException) {
            false
        }
        if (canRedirect) {
            // The dial itself becomes the reply call, so the watch sees it connect, never fail.
            redirectCall(handle, ReplyCallService.prepareRedirect(applicationContext, binding), false)
        } else {
            // During a real call, or when call state can't be read. A reply starts only on an idle phone.
            cancelCall()
            ReplyCallService.startWhenIdle(applicationContext, binding)
        }
    }

    /**
     * Looks for a code first, because a code must never leak out as a real call. Codes are checked
     * against emergency numbers when saved, and Android doesn't send emergency calls here at all,
     * so a failed check on a saved code doesn't make it an emergency number.
     */
    private fun findCode(handle: Uri): CodeBinding? {
        if (handle.scheme != "tel") return null
        val code = PhoneNumberUtils.stripSeparators(handle.schemeSpecificPart) ?: return null
        val binding = try {
            Store.find(code)
        } catch (e: RuntimeException) {
            Log.w(TAG, "code lookup failed", e)
            null
        } ?: return null
        val emergency = try {
            getSystemService(TelephonyManager::class.java).isEmergencyNumber(binding.code)
        } catch (e: RuntimeException) {
            Log.w(TAG, "emergency check failed", e)
            false
        }
        return binding.takeUnless { emergency }
    }

    private companion object {
        const val TAG = "Digix"
    }
}
