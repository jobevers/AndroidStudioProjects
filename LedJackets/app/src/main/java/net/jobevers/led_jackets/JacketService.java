package net.jobevers.led_jackets;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.List;

public class JacketService extends Service {
    private String TAG = "JacketService";
    private List<BluetoothDevice> devices;
    private StatusListener listener;

    class JacketServiceBinder extends Binder {
        JacketService getService() { return JacketService.this; }
    }

    interface StatusListener {
        void setStatus(BluetoothDevice device, String status);
    }

    private final IBinder binder;

    public JacketService() {
        binder = new JacketServiceBinder();
    }

    public void connect(List<BluetoothDevice> devices) {
        Log.d(TAG, "Connect");
        this.devices = devices;
        // need to set the listener before connecting
        for (BluetoothDevice dev : devices) {
            listener.setStatus(dev, "INITIATING");
        }
    }

    public void setStatusListener(StatusListener listener) {
        this.listener = listener;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
