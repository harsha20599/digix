package dev.watchtrigger.probe;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.telecom.DisconnectCause;
import android.util.Log;

/** Echo test: plays a beep, records from the call mic, plays it back, three times. */
public class AudioTestService extends Service {
    private static final int RATE = 16000;
    private static final int RECORD_SECONDS = 4;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(2, Probe.builder(this, "Voice test", "Talk after the beep").build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
            Log.i(Probe.TAG, "foreground service started with mic type");
        } catch (RuntimeException e) {
            Log.e(Probe.TAG, "MIC BLOCKED: foreground start refused", e);
            endCall();
            stopSelf();
            return START_NOT_STICKY;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                runTest();
            }
        }, "probe-audio").start();
        return START_NOT_STICKY;
    }

    private void runTest() {
        AudioManager audio = getSystemService(AudioManager.class);
        try {
            Thread.sleep(1500);
            logRoute(audio, "before");
            useBluetoothIfNeeded(audio);
            Thread.sleep(500);
            logRoute(audio, "start");
            for (int round = 1; round <= 3 && ProbeConnectionService.current != null; round++) {
                playBeep();
                short[] voice = record(round);
                play(voice);
            }
            Log.i(Probe.TAG, "echo test finished");
        } catch (Exception e) {
            Log.e(Probe.TAG, "echo test failed", e);
        } finally {
            audio.clearCommunicationDevice();
            endCall();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void useBluetoothIfNeeded(AudioManager audio) {
        AudioDeviceInfo device = audio.getCommunicationDevice();
        if (device != null && device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return;
        }
        for (AudioDeviceInfo d : audio.getAvailableCommunicationDevices()) {
            if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                Log.i(Probe.TAG, "forcing comm device " + d.getProductName()
                        + " ok=" + audio.setCommunicationDevice(d));
                return;
            }
        }
        Log.w(Probe.TAG, "no bluetooth call device available");
    }

    private void logRoute(AudioManager audio, String label) {
        AudioDeviceInfo device = audio.getCommunicationDevice();
        Log.i(Probe.TAG, label + " mode=" + audio.getMode() + " commDevice="
                + (device == null ? "null" : device.getProductName() + " type=" + device.getType()));
    }

    private short[] record(int round) throws InterruptedException {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE));
        short[] pcm = new short[RATE * RECORD_SECONDS];
        int total = 0;
        try {
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(Probe.TAG, "MIC BLOCKED: recorder did not initialize");
                return pcm;
            }
            rec.startRecording();
            AudioDeviceInfo routed = rec.getRoutedDevice();
            Log.i(Probe.TAG, "round " + round + " recording from "
                    + (routed == null ? "null" : routed.getProductName() + " type=" + routed.getType()));
            while (total < pcm.length) {
                int n = rec.read(pcm, total, pcm.length - total);
                if (n <= 0) {
                    Log.e(Probe.TAG, "read error " + n);
                    break;
                }
                total += n;
            }
        } finally {
            if (rec.getState() == AudioRecord.STATE_INITIALIZED) rec.stop();
            rec.release();
        }
        double sum = 0;
        int peak = 0;
        for (int i = 0; i < total; i++) {
            sum += pcm[i] * (double) pcm[i];
            peak = Math.max(peak, Math.abs((int) pcm[i]));
        }
        Log.i(Probe.TAG, "round " + round + " mic rms=" + Math.round(Math.sqrt(sum / Math.max(1, total)))
                + " peak=" + peak + " samples=" + total);
        return pcm;
    }

    private void playBeep() {
        short[] pcm = new short[RATE / 2];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (Math.sin(2 * Math.PI * 880 * i / RATE) * 12000);
        }
        play(pcm);
    }

    private void play(short[] pcm) {
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(RATE)
                .build();
        try {
            track.play();
            track.write(pcm, 0, pcm.length);
            track.stop();
            Thread.sleep(pcm.length * 1000L / RATE / 4);
            AudioDeviceInfo routed = track.getRoutedDevice();
            Log.i(Probe.TAG, "played to " + (routed == null ? "null" : routed.getProductName() + " type=" + routed.getType()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            track.release();
        }
    }

    private void endCall() {
        ProbeConnectionService.ProbeCall call = ProbeConnectionService.current;
        if (call != null) {
            call.finish(DisconnectCause.LOCAL);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
