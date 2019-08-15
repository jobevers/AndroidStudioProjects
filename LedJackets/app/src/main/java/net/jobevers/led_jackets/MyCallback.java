package net.jobevers.led_jackets;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class MyCallback extends BluetoothGattCallback {
    private String TAG;
    private int idx;
    private ColorBox parent;

    // Copied from LeTerminal, but MTU will really always be 23.
    private static final int MAX_MTU = 512; // BLE standard does not limit, some BLE 4.2 devices support 251, various source say that Android has max 512
    private int payloadSize = 20;
    // CCCD = client characteristic configuration
    private static final UUID BLUETOOTH_LE_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_CC254X_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_CC254X_CHAR_RW = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    BluetoothGattCharacteristic characteristic;
    BluetoothGatt gatt;
    private int hue = 0;
    private long time;
    private boolean connected = false;
    private boolean wait = true;
    private int nextMessage;

    public MyCallback(int idx, ColorBox parent) {
        super();
        this.idx = idx;
        this.parent = parent;
        TAG = "MyCallback-" + idx;
    }

    @Override
    public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        super.onPhyUpdate(gatt, txPhy, rxPhy, status);
        Log.i(TAG, "onPhyUpdate");
    }

    @Override
    public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        super.onPhyRead(gatt, txPhy, rxPhy, status);
        Log.i(TAG, "onPhyRead");
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        Log.i(TAG, "onConnectionStateChange " + status + " " + newState);
        characteristic = null;
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // Lets.. try to re-connect, I guess.
            this.connected = false;
            gatt.connect();
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
        gatt.requestMtu(MAX_MTU);
    }

    public void sendColor(int hue) {
        if (!connected) {
            Log.i(TAG, "Waiting to be connected");
            return;
        }
        if (wait) {
            Log.i(TAG, "Still waiting for acknowledgement so skipping. Sad.");
            return;
        }
        if (characteristic == null) {
            // Shouldn't every get here, connected is set after characteristic.
            Log.i(TAG, "Waiting for service discovery");
            return;
        }
        this.hue = hue;
        sendMessage(0);
    }

    private void sendMessage(int msg) {
        // TODO: a queue would be better here so that
        // I know that I'm not overwhelming the writeCharacteristic
        // and should wait until onCharacteristicWrite has finished
        int sat = 0xFF;
        int val = 0xFF;
        byte[] data = new byte[payloadSize];
        data[0] = (byte) msg;
        // For some reason I convinced myself that interlaced is better
        // But it the way the arduino is coded right now I wait
        // for the entire frame to be received, so it doesn't really matter.
        // Maybe some point in the future I could be more clever....
        for (int i = 0; (2 * i + msg) < 25; i++) {
            data[i + 1] = pack((byte) (hue + 10 * (2 * i + msg)), (byte) sat, (byte) val);
        }
        characteristic.setValue(data);
        Log.i(TAG, "Writing msg: " + msg + ". color " + hue + " : " + (hue & 0xF0));
        wait = true;
        nextMessage = (msg + 1) % 2;
        gatt.writeCharacteristic(characteristic);
    }

    public byte pack(byte hue, byte sat, byte val) {
        // Grab 4 most significant bits of the hue
        // The 2 most significant bits of the sat / val
        // The latter two need to be shifted 4 and 6 bits respectively
        // The end result is 8 bits: hhhhssvv.
        return (byte) ((hue & 0xF0) | ((sat & 0xC0) >> 4) | ((val & 0xC0) >> 6));
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        Log.i(TAG, "onCharacteristicWrite");
        Log.i(TAG, "Trigger characteristicRead");
        gatt.readCharacteristic(characteristic);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        Log.i(TAG, "onCharacteristicChanged");
        byte[] val = characteristic.getValue();
        int msg = (int) val[0];
        Log.i(TAG, "onCharacteristicRead: " + val);
        Log.i(TAG, "onCharacteristicRead: msg: " + msg);
        // TODO: would be nice to get the actual message from the arduino
        // but in the meantime, just assume things have been sent 0 and then 1.
        if (nextMessage == 1) {
            // TODO: can I be more aggressive here and send the second message
            // right after the onCharacteristicWrite?
            sendMessage(1);
        } else {
            wait = false;
        }
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorRead(gatt, descriptor, status);
        Log.i(TAG, "onDescriptorRead");
    }

    @Override
    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
        super.onReliableWriteCompleted(gatt, status);
        Log.i(TAG, "onReliableWriteCompleted");
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);
        Log.i(TAG, "onReadRemoteRssi");
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        Log.d(TAG, "mtu size " + mtu + ", status=" + status);
        super.onMtuChanged(gatt, mtu, status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            payloadSize = mtu - 3;
            Log.d(TAG, "payload size " + payloadSize);
        }
        connectCharacteristics(gatt);
    }

    private void connectCharacteristics(BluetoothGatt gatt) {
        int writeProperties = characteristic.getProperties();
        if ((writeProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE +     // Microbit,HM10-clone have WRITE
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) { // HM10,TI uart have only WRITE_NO_RESPONSE
            new IOException("write characteristic not writable");
            return;
        }
        if(!gatt.setCharacteristicNotification(characteristic,true)) {
            new IOException("no notification for read characteristic");
            return;
        }
        BluetoothGattDescriptor readDescriptor = characteristic.getDescriptor(BLUETOOTH_LE_CCCD);
        if(readDescriptor == null) {
            new IOException("no CCCD descriptor for read characteristic");
            return;
        }
        int readProperties = characteristic.getProperties();
        if((readProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            Log.d(TAG, "enable read indication");
            readDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        }else if((readProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            Log.d(TAG, "enable read notification");
            readDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            new IOException("no indication/notification for read characteristic ("+readProperties+")");
            return;
        }
        Log.d(TAG,"writing read characterictic descriptor");
        if(!gatt.writeDescriptor(readDescriptor)) {
            new IOException("read characteristic CCCD descriptor not writable");
        }
        // Continued async in onDescriptorWrite
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        if (descriptor.getCharacteristic() == characteristic) {
            Log.d(TAG, "writing read characteristic descriptor finished, status=" + status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                new IOException("write descriptor failed");
            } else {
                // onCharacteristicChanged with incoming data can happen after writeDescriptor(ENABLE_INDICATION/NOTIFICATION)
                // before confirmed by this method, so receive data can be shown before device is shown as 'Connected'.
                // We're finally connected!
                connected = true;
                wait = false;
                parent.onConnection(this.idx);
                Log.d(TAG, "connected");
            }
        } else {
            Log.d(TAG, "unknown write descriptor finished, status=" + status);
        }
    }
}
