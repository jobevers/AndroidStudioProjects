package net.jobevers.led_jackets;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class PatternService extends Service {
    // Use this to post stuff back to the UI
    private final Handler mainLooper;

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
}
