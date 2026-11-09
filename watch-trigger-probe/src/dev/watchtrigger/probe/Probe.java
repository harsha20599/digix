package dev.watchtrigger.probe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

/** Shared constants and helpers for the probe. */
final class Probe {
    static final String TAG = "WatchProbe";

    /** Dial from the watch: caught and cancelled, posts a notification. */
    static final String CODE_TRIGGER = "00001";
    /** Dial from the watch: caught and cancelled, then starts a fake call with an echo test. */
    static final String CODE_VOICE = "00002";

    static final String CHANNEL = "probe";

    private Probe() {}

    static PhoneAccountHandle accountHandle(Context c) {
        return new PhoneAccountHandle(new ComponentName(c, ProbeConnectionService.class), "probe");
    }

    static void registerAccount(Context c) {
        PhoneAccount account = PhoneAccount.builder(accountHandle(c), "Watch Trigger Probe")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .build();
        c.getSystemService(TelecomManager.class).registerPhoneAccount(account);
        Log.i(TAG, "phone account registered");
    }

    static Notification.Builder builder(Context c, String title, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(
                new NotificationChannel(CHANNEL, "Probe", NotificationManager.IMPORTANCE_HIGH));
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text);
    }

    static void notify(Context c, int id, String title, String text) {
        c.getSystemService(NotificationManager.class).notify(id, builder(c, title, text).build());
    }
}
