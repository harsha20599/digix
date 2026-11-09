package dev.watchtrigger.probe;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

/** Provides the fake call that keeps the watch's mic and speaker open. */
public class ProbeConnectionService extends ConnectionService {
    static volatile ProbeCall current;

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle account, ConnectionRequest request) {
        Log.i(Probe.TAG, "fake call created");
        final ProbeCall call = new ProbeCall();
        call.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        call.setCallerDisplayName("Claude test", TelecomManager.PRESENTATION_ALLOWED);
        call.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        call.setConnectionCapabilities(Connection.CAPABILITY_SUPPORT_HOLD | Connection.CAPABILITY_HOLD);
        call.setAudioModeIsVoip(true);
        call.setDialing();
        current = call;
        final android.content.Context app = getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                call.setActive();
                call.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
                Log.i(Probe.TAG, "fake call active, bluetooth route requested");
                try {
                    app.startForegroundService(new Intent(app, AudioTestService.class));
                    Log.i(Probe.TAG, "audio service start requested");
                } catch (RuntimeException e) {
                    Log.e(Probe.TAG, "audio service start failed", e);
                }
            }
        }, 500);
        return call;
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle account, ConnectionRequest request) {
        Log.e(Probe.TAG, "fake call refused by Telecom");
    }

    static final class ProbeCall extends Connection {
        @Override
        public void onCallAudioStateChanged(CallAudioState state) {
            Log.i(Probe.TAG, "route=" + CallAudioState.audioRouteToString(state.getRoute())
                    + " device=" + state.getActiveBluetoothDevice());
        }

        @Override
        public void onPlayDtmfTone(char c) {
            Log.i(Probe.TAG, "DTMF key=" + c);
        }

        @Override
        public void onDisconnect() {
            Log.i(Probe.TAG, "fake call ended by user or watch");
            finish(DisconnectCause.LOCAL);
        }

        @Override
        public void onAbort() {
            Log.i(Probe.TAG, "fake call aborted");
            finish(DisconnectCause.CANCELED);
        }

        @Override
        public void onHold() {
            setOnHold();
        }

        @Override
        public void onUnhold() {
            setActive();
        }

        void finish(int cause) {
            setDisconnected(new DisconnectCause(cause));
            destroy();
            current = null;
        }
    }
}
