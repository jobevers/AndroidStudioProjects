package net.jobevers.led_jackets;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.support.v7.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import net.jobevers.led_jackets.R;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class ColorBox extends AppCompatActivity {

    private String TAG = "ColorBox";
    View colorBox;
    int hue = 0;
    private Handler mHandler;
    private int nDevices;
    private ArrayList<String> deviceAddresses = new ArrayList<>();
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private ArrayList<BluetoothGatt> bluetoothGatts = new ArrayList<>();
    private ArrayList<MyCallback> callbacks = new ArrayList<>();
    private long nextKeyFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.color_box);
        colorBox = findViewById(R.id.colorBox);

        if (savedInstanceState == null) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            // Pass through the bundle and get all of the devices
            Bundle bundle = getIntent().getExtras();
            int nDevices = bundle.getInt("nDevices");
            for (int i = 0; i < nDevices; i++) {
                String address = bundle.getString("device" + i);
                deviceAddresses.add(address);
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                devices.add(device);
                MyCallback callback = new MyCallback(i);
                callbacks.add(callback);
                BluetoothGatt gatt = device.connectGatt(getApplicationContext(), false, callback);
                bluetoothGatts.add(gatt);
                Log.i(TAG, "GATT Connected: " + i);
            }
        } else {
            // What do I do here?
        }

        mHandler = new Handler();
        mColorSetter.run();
        nextKeyFrame = System.currentTimeMillis();
    }

    Runnable mColorSetter = new Runnable() {
        @Override
        public void run() {
            try {
                long now = System.currentTimeMillis();
                float[] hsv = {hue, 1, 1};
                colorBox.setBackgroundColor(Color.HSVToColor(hsv));
                hue = (hue + 1) % 360;
                if (now >= nextKeyFrame) {
                    int hueByte = (int) (hue * 255.0 / 360);
                    for (MyCallback callback : callbacks) {
                        callback.sendColor(hueByte);
                    }
                    nextKeyFrame += 250;
                }
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                mHandler.postDelayed(mColorSetter, 30);
            }
        }
    };
}
