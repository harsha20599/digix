package com.littyl.digix.engine;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;

/**
 * Switches the hotspot on Android 15 and below with only the "Modify system settings" permission.
 * This older interface isn't public, and Android 16 refuses it for normal apps. A false result means
 * the request didn't go through, so the caller moves on to another route.
 */
final class LegacyTether {
    private static final int WIFI = 0;

    private LegacyTether() {}

    static boolean start(Context context, final Runnable onStarted, final Runnable onFailed) {
        try {
            Object manager = context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            Method method = ConnectivityManager.class.getMethod(
                    "startTethering",
                    int.class,
                    boolean.class,
                    Class.forName("android.net.ConnectivityManager$OnStartTetheringCallback"),
                    Handler.class);
            Object callback = new android.net.ConnectivityManager$OnStartTetheringCallback() {
                @Override
                public void onTetheringStarted() {
                    onStarted.run();
                }

                @Override
                public void onTetheringFailed() {
                    onFailed.run();
                }
            };
            method.invoke(manager, WIFI, false, callback, new Handler(Looper.getMainLooper()));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    static boolean stop(Context context) {
        try {
            Object manager = context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            ConnectivityManager.class.getMethod("stopTethering", int.class).invoke(manager, WIFI);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
