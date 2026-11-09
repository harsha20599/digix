package dev.watchtrigger.probe;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.CallRedirectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

/**
 * Sees every outgoing call before it is placed. Only the two test codes are acted on.
 * Anything else, or any failure, passes through unchanged. Other numbers are never logged.
 */
public class ProbeRedirectionService extends CallRedirectionService {

    @Override
    public void onPlaceCall(Uri handle, PhoneAccountHandle account, boolean allowInteractive) {
        String digits = "";
        try {
            digits = handle.getSchemeSpecificPart().replaceAll("[^0-9*#]", "");
        } catch (RuntimeException e) {
            Log.w(Probe.TAG, "could not read number", e);
        }

        boolean ours = account != null && account.equals(Probe.accountHandle(this));
        if (ours || !(digits.equals(Probe.CODE_TRIGGER) || digits.equals(Probe.CODE_VOICE))) {
            Log.i(Probe.TAG, "pass through (ours=" + ours + ")");
            placeCallUnmodified();
            return;
        }

        Log.i(Probe.TAG, "CAUGHT code=" + digits + " interactive=" + allowInteractive);
        cancelCall();
        Log.i(Probe.TAG, "cancelled code=" + digits);

        if (digits.equals(Probe.CODE_TRIGGER)) {
            Probe.notify(this, 1, "Trigger caught", "Code " + digits + " ran on the phone");
        } else {
            final Context app = getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    startFakeCall(app);
                }
            }, 800);
        }
    }

    private static void startFakeCall(Context app) {
        try {
            TelecomManager telecom = app.getSystemService(TelecomManager.class);
            PhoneAccountHandle handle = Probe.accountHandle(app);
            Log.i(Probe.TAG, "outgoing permitted=" + telecom.isOutgoingCallPermitted(handle));
            Bundle extras = new Bundle();
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
            telecom.placeCall(Uri.fromParts("tel", Probe.CODE_VOICE, null), extras);
            Log.i(Probe.TAG, "fake call requested");
        } catch (RuntimeException e) {
            Log.e(Probe.TAG, "fake call failed", e);
        }
    }
}
