package com.littyl.digix.shell;

import android.net.IIntResultListener;
import android.os.IBinder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Starts or stops Wi-Fi tethering with the phone's saved hotspot settings. Runs in its own process
 * with shell privileges through Shizuku, so it uses system interfaces a normal app cannot reach.
 * Usage: app_process / com.littyl.digix.shell.HotspotTool start|stop
 */
public final class HotspotTool {
    private static final int TETHERING_WIFI = 0;
    private static final int SCOPE_GLOBAL = 1;
    private static final String CALLER = "com.android.shell";

    private HotspotTool() {}

    public static void main(String[] args) throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        IBinder binder = (IBinder) serviceManager.getMethod("getService", String.class).invoke(null, "tethering");
        if (binder == null) {
            System.exit(2);
        }
        Object connector = Class.forName("android.net.ITetheringConnector$Stub")
                .getMethod("asInterface", IBinder.class)
                .invoke(null, binder);

        final CountDownLatch done = new CountDownLatch(1);
        final int[] result = {-1};
        IIntResultListener listener = new IIntResultListener.Stub() {
            @Override
            public void onResult(int status) {
                result[0] = status;
                done.countDown();
            }
        };

        boolean start = "start".equals(args[0]);
        for (Method method : connector.getClass().getMethods()) {
            if (start && method.getName().equals("startTethering")) {
                method.invoke(connector, buildRequest(), CALLER, null, listener);
                break;
            }
            if (!start && method.getName().equals("stopTethering")) {
                method.invoke(connector, TETHERING_WIFI, CALLER, null, listener);
                break;
            }
        }
        boolean answered = done.await(8, TimeUnit.SECONDS);
        System.exit(answered && result[0] == 0 ? 0 : 1);
    }

    private static Object buildRequest() throws Exception {
        Object request = Class.forName("android.net.TetheringRequestParcel").getConstructor().newInstance();
        set(request, "tetheringType", TETHERING_WIFI);
        set(request, "exemptFromEntitlementCheck", true);
        set(request, "showProvisioningUi", false);
        set(request, "connectivityScope", SCOPE_GLOBAL);
        set(request, "uid", android.os.Process.myUid());
        set(request, "packageName", CALLER);
        return request;
    }

    /** Fields differ between Android versions, so a missing one is not an error. */
    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getField(name);
            field.set(target, value);
        } catch (ReflectiveOperationException ignored) {
            // Older or newer parcel without this field.
        }
    }
}
