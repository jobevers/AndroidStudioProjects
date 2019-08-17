package net.jobevers.led_jackets;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import net.jobevers.led_jackets.R;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import processing.android.PFragment;

public class ColorBox extends AppCompatActivity implements PatternGenerator.PatternDrawListener {

    private String TAG = "ColorBox";
    private int KEY_FRAME_DURATION = 250;
    FrameLayout frame;
    private Handler mHandler;
    private int nDevices;
    private ArrayList<String> deviceAddresses = new ArrayList<>();
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private ArrayList<BluetoothGatt> bluetoothGatts = new ArrayList<>();
    private ArrayList<MyCallback> callbacks = new ArrayList<>();
    private ArrayList<Boolean> connected = new ArrayList<>();
    private long nextKeyFrame;
    private PatternGenerator patternGenerator;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.color_box);
        frame = findViewById(R.id.processing_frame);

        if (savedInstanceState == null) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            // Pass through the bundle and get all of the devices
            Bundle bundle = getIntent().getExtras();
            nDevices = bundle.getInt("nDevices");
            Log.i(TAG, "nDevices: " + nDevices);
            for (int i = 0; i < nDevices; i++) {
                String address = bundle.getString("device" + i);
                deviceAddresses.add(address);
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                devices.add(device);
                MyCallback callback = new MyCallback(i, this);
                callbacks.add(callback);
                connected.add(false);
            }
            connectGatt(0);
        } else {
            // What do I do here?
        }

        mHandler = new Handler();
    }

    void connectGatt(int idx) {
        BluetoothDevice device = devices.get(idx);
        MyCallback callback = callbacks.get(idx);
        BluetoothGatt gatt = device.connectGatt(getApplicationContext(), false, callback);
        bluetoothGatts.add(gatt);
        Log.i(TAG, "GATT Connecting: " + idx);
    }

    void onConnection(int idx) {
        connected.set(idx, true);
        // set to red on connection
        callbacks.get(idx).sendColor(0x00);
        for (int i = 0; i < nDevices; i++) {
            if (!this.connected.get(i)) {
                connectGatt(i);
                return;
            }
        }
        // if we get here, all the devices are connected
        // so we can start running
        nextKeyFrame = System.currentTimeMillis();
        Log.i(TAG, "EVERYTHING IS CONNECTED");
        // This code is from the example at
        // https://android.processing.org/tutorials/android_studio/index.html
        patternGenerator = new PatternGenerator();
        patternGenerator.setDrawListener(this);
        PFragment fragment = new PFragment(patternGenerator);
        fragment.setView(frame, this);

        // This is just for logging
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        for (BluetoothDevice dev : bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)) {
            Log.i(TAG, "Connected: " + dev.getAddress());
        }
    }

    private boolean areAllConnected() {
        for (boolean b : this.connected) if (!b) return false;
        return true;
    }

    public void onFrame(int hue) {
        long now = System.currentTimeMillis();
        if (now >= nextKeyFrame) {
            for (MyCallback callback : callbacks) {
                callback.sendColor(hue);
            }
            nextKeyFrame += KEY_FRAME_DURATION;
        }
    }

    @Override
    public void onFrame(int frameCount, int[] pixels) {
        int x = 1 / 0;
    }

//    Runnable mColorSetter = new Runnable() {
//        @Override
//        public void run() {
//            try {
//                long now = System.currentTimeMillis();
//                float[] hsv = {hue, 1, 1};
//                colorBox.setBackgroundColor(Color.HSVToColor(hsv));
//                hue = (hue + 1) % 360;
//                if (now >= nextKeyFrame) {
//                    int hueByte = (int) (hue * 255.0 / 360);
//                    for (MyCallback callback : callbacks) {
//                        callback.sendColor(hueByte);
//                    }
//                    nextKeyFrame += 250;
//                }
//            } finally {
//                // 100% guarantee that this always happens, even if
//                // your update method throws an exception
//                mHandler.postDelayed(mColorSetter, 30);
//            }
//        }
//    };
}
