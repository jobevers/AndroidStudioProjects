package net.jobevers.led_jackets;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.ColorInt;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class PatternService extends Service {
    private static final String TAG = "PatternService";
    // Use this to post stuff back to the UI
    ArrayList<PatternDrawListener> drawListeners = new ArrayList<>();
    private final Handler mainLooper;
    FrameThread p;

    public interface PatternDrawListener {
        void onFrame(int frameCount, HSV[] pixels);
    }

    class PatternServiceBinder extends Binder {
        PatternService getService() {
            return PatternService.this;
        }
    }

    private final IBinder binder;

    public PatternService() {
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new PatternService.PatternServiceBinder();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void addDrawListener(PatternDrawListener drawListener) {
        if (!drawListeners.contains(drawListener)) {
            this.drawListeners.add(drawListener);
        } else {
            Log.w(TAG, "drawListener is already present");
        }
    }

    public void run(int nJackets) {
        if (p == null) {
            p = new FrameThread(nJackets);
            new Thread(p).start();
        } else {
            Log.w(TAG, "FrameThread is started");
        }
    }

    public void triggerReset() {
        // Sure, this isn't threadsafe.  whatever.
        p.resetPill = true;
    }

    class FrameThread implements Runnable {
        String TAG = "FrameThread";
        int nJackets;
        int frameLength = 33;
        boolean poisonPill = false;
        boolean resetPill = false;
        int frameCount = 0;
        int hue = 0;
        HSV pixels[];

        FrameThread(int nJackets) {
            this.nJackets = nJackets;
            // TODO how will this be laid out?
            pixels = new HSV[nJackets * 25];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = new HSV(hue, 255, 255);
            }
        }

        private void draw() {
            for (int i = 0; i < pixels.length; i++) {
                pixels[i].hue = hue;
            }
            hue = 128; //(hue + 1) & 0xFF;
        }

        private void doReset() {
            frameCount = 0;
            resetPill = false;
        }

        public void run() {
            while (!poisonPill) {
                long start = System.currentTimeMillis();
                if (resetPill) {
                    doReset();
                }
                draw();
                mainLooper.post(() -> {
                    for (PatternDrawListener dl : drawListeners) {
                        dl.onFrame(frameCount, pixels);
                    }
                });
                long nextFrame = start += frameLength;
                long end = System.currentTimeMillis();
                long sleep = nextFrame - end;
                if (sleep > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(sleep);
                    } catch (InterruptedException ex) {
                        Log.w(TAG, "Can't sleep, got interrupted");
                        break;
                    }
                } else {
                    // No time for sleep!
                    Log.w(TAG, "Frame took " + (end - start) + ", which is too long");
                }
                frameCount++;
            }
        }
    }
}
