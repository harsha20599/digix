package android.net;

// Compile-time stand-in only. The real class comes from the phone at runtime.
public interface IIntResultListener extends android.os.IInterface {
    void onResult(int status) throws android.os.RemoteException;

    abstract class Stub extends android.os.Binder implements IIntResultListener {
        public Stub() {}

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }
    }
}
