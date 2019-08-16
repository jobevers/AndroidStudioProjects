package net.jobevers.led_jackets;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JacketService extends Service {
    // CCCD = client characteristic configuration
    private static final UUID BLUETOOTH_LE_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_CC254X_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_CC254X_CHAR_RW = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    private String TAG = "JacketService";
    private List<BluetoothDevice> devices;
    private ArrayList<MyGattCallback> callbacks = new ArrayList<>();
    private StatusListener listener;

    class JacketServiceBinder extends Binder {
        JacketService getService() {
            return JacketService.this;
        }
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
        for (int i = 0; i < devices.size(); i++) {
            BluetoothDevice dev = devices.get(i);
            MyGattCallback callback = new MyGattCallback(i);
            callbacks.add(callback);
            dev.connectGatt(this, false, callback);
        }
    }

    public void setStatusListener(StatusListener listener) {
        this.listener = listener;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // This is a very thorough implementation of thse callbacks
    // https://github.com/RatioLabs/BLEService/blob/master/DeviceService/src/com/ratio/deviceService/BTLEDeviceManager.java#L490
    // and this seems decent as well:
    // https://intersog.com/blog/tech-tips/how-to-work-properly-with-bt-le-on-android/
    // Of note is that there is NO queue for the commands or callbacks
    // so need to make some effort to not block the thread the callbacks
    // are called on.
    // I'm currently only sending data, waiting for a response, sending data
    // etc. so I should be okay.
    class MyGattCallback extends BluetoothGattCallback {
        private String TAG;
        private int idx;

        private int payloadSize = 20;
        BluetoothGattCharacteristic characteristic;
        BluetoothGatt gatt;
        private boolean connected = false;
        private boolean wait = true;
        // generally want to wait until we get a confirmation message before sending
        // the next frame.  But if its been too long and were still connected, try again.
        private long waitOverride = Long.MAX_VALUE;
        // keep track of continuous failure.  If this gets too high we'll disconnect
        // to force a reconnect;
        private int failures = 0;
        // Each frame consists of 2 messages.  This gets set to 1 after we send the first message
        private int nextMessage;

        private final Handler mainLooper;

        MyGattCallback(int idx) {
            mainLooper = new Handler(Looper.getMainLooper());
            this.idx = idx;
            TAG = "MyGattCallback-" + idx;
        }

        private void setStatus(String status) {
            mainLooper.post(() -> {
                listener.setStatus(devices.get(idx), status);
            });
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i(TAG, "onConnectionStateChange status = " + status + " state = " + newState);
            characteristic = null;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // if there was a status error, DO NOT EXECUTE ANYMORE COMMANDS ON THIS DEVICE, and disconnect from it.
                gatt.disconnect();
                setStatus("ERROR");
            } else {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "calling discoverServices()");
                    gatt.discoverServices();
                    setStatus("DISCOVERING SERVICES");
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    setStatus("DISCONNECTED");
                    // Lets.. try to re-connect, I guess.
                    // TODO: add in a retry count
                    // TODO: add in a timer on connect because sometimes it just seems to fail
                    connected = false;
                    waitOverride = Integer.MAX_VALUE;
                    Log.i(TAG, "calling gatt.connect()");
                    gatt.connect();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            this.gatt = gatt;
            Log.i(TAG, "onServicesDiscovered");
            BluetoothGattService service = gatt.getService(BLUETOOTH_LE_CC254X_SERVICE);
            characteristic = service.getCharacteristic(BLUETOOTH_LE_CC254X_CHAR_RW);
            Log.d(TAG, "request max MTU");
            connectCharacteristics(gatt);
        }

        // This does three things:
        // 1. Check that we can actually write to the device
        // 2. Setup up local read notifications
        // 3. Setup up remote read notifications
        // See https://stackoverflow.com/questions/22817005/why-does-setcharacteristicnotification-not-actually-enable-notifications
        // for an explanation for the last two.
        private void connectCharacteristics(BluetoothGatt gatt) {

            int writeProperties = characteristic.getProperties();
            if ((writeProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE +     // Microbit,HM10-clone have WRITE
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) { // HM10,TI uart have only WRITE_NO_RESPONSE
                Log.e(TAG, "write characteristic not writable");
                return;
            }
            // Setup notifications locally
            // This will trigger onCharacteristicChanged
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                Log.e(TAG, "no notification for read characteristic");
                return;
            }
            // Tell the device that we want notifications when there is data to read.
            BluetoothGattDescriptor readDescriptor = characteristic.getDescriptor(BLUETOOTH_LE_CCCD);
            if (readDescriptor == null) {
                Log.e(TAG, "no CCCD descriptor for read characteristic");
                return;
            }
            int readProperties = characteristic.getProperties();
            if ((readProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                Log.d(TAG, "enable read indication");
                readDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            } else if ((readProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                Log.d(TAG, "enable read notification");
                readDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else {
                Log.e(TAG, "no indication/notification for read characteristic (" + readProperties + ")");
                return;
            }
            Log.d(TAG, "writing read characteristic descriptor");
            if (!gatt.writeDescriptor(readDescriptor)) {
                Log.e(TAG, "read characteristic CCCD descriptor not writable");
            }
            // Continued async in onDescriptorWrite
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (descriptor.getCharacteristic() == characteristic) {
                Log.d(TAG, "writing read characteristic descriptor finished, status=" + status);
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    // Well.. I don't know.  Lets disconnect and try again.
                    gatt.disconnect();
                } else {
                    // onCharacteristicChanged with incoming data can happen after writeDescriptor(ENABLE_INDICATION/NOTIFICATION)
                    // before confirmed by this method, so receive data can be shown before device is shown as 'Connected'.
                    // We're finally connected!
                    connected = true;
                    wait = false;
                    setStatus("CONNECTED");
                    //parent.onConnection(this.idx);
                    Log.d(TAG, "connected");
                    // TODO: remove this code, but putting it in here so that
                    // I can see some visual change
                    characteristic.setValue(
                            new byte[]{(byte) 0x00, (byte) 0xFF, (byte) 0xFF,
                                    (byte) 0xFF, (byte) 0xFF});
                    gatt.writeCharacteristic(characteristic);
                }
            } else {
                Log.d(TAG, "unknown write descriptor finished, status=" + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.i(TAG, "onCharacteristicWrite");
            // TODO: remove this code, but putting it in here so that
            // I can see some visual change
            characteristic.setValue(
                    new byte[]{(byte) 0x01, (byte) 0xFF, (byte) 0xFF,
                            (byte) 0xFF, (byte) 0xFF});
            gatt.writeCharacteristic(characteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte[] val = characteristic.getValue();
            Log.i(TAG, "onCharacteristicChanged: " + bytesToHex(val));
            int msg = val[0] & 0xFF;
            Log.i(TAG, "onCharacteristicChanged: msg: " + msg);
        }

        private final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

        public String bytesToHex(byte[] bytes) {
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = HEX_ARRAY[v >>> 4];
                hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        }

    }
}
