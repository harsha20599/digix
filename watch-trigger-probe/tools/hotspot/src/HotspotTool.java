import android.net.IIntResultListener;
import android.os.IBinder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Starts or stops Wi-Fi tethering with the phone's saved hotspot settings.
 * Runs with shell privileges, the same level Shizuku provides.
 * Usage: app_process / HotspotTool start|stop
 */
public class HotspotTool {
    private static final int TETHERING_WIFI = 0;
    private static final String CALLER = "com.android.shell";

    public static void main(String[] args) throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        IBinder binder = (IBinder) serviceManager.getMethod("getService", String.class).invoke(null, "tethering");
        if (binder == null) {
            System.out.println("no tethering service");
            return;
        }
        Class<?> stub = Class.forName("android.net.ITetheringConnector$Stub");
        Object connector = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);

        final CountDownLatch done = new CountDownLatch(1);
        final int[] result = {-1};
        IIntResultListener listener = new IIntResultListener.Stub() {
            @Override
            public void onResult(int status) {
                result[0] = status;
                done.countDown();
            }
        };

        if ("start".equals(args[0])) {
            Object request = buildRequest();
            for (Method m : connector.getClass().getMethods()) {
                if (m.getName().equals("startTethering")) {
                    System.out.println("calling " + m);
                    m.invoke(connector, request, CALLER, null, listener);
                    break;
                }
            }
        } else {
            for (Method m : connector.getClass().getMethods()) {
                if (m.getName().equals("stopTethering")) {
                    System.out.println("calling " + m);
                    m.invoke(connector, TETHERING_WIFI, CALLER, null, listener);
                    break;
                }
            }
        }
        boolean answered = done.await(8, TimeUnit.SECONDS);
        System.out.println(args[0] + " answered=" + answered + " status=" + result[0] + " (0 means success)");
    }

    private static Object buildRequest() throws Exception {
        Class<?> cls = Class.forName("android.net.TetheringRequestParcel");
        Object request = cls.getConstructor().newInstance();
        set(request, "tetheringType", TETHERING_WIFI);
        set(request, "exemptFromEntitlementCheck", true);
        set(request, "showProvisioningUi", false);
        set(request, "connectivityScope", 1);
        set(request, "uid", android.os.Process.myUid());
        set(request, "packageName", CALLER);
        return request;
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getField(name);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            System.out.println("field not present: " + name);
        }
    }
}
