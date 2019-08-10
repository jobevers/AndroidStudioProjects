package net.jobevers.led_jackets;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class MyCallback extends BluetoothGattCallback {
    private int idx;
    private String TAG;

    private static final UUID BLUETOOTH_LE_CC254X_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_CC254X_CHAR_RW = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    BluetoothGattCharacteristic characteristic;
    BluetoothGatt gatt;
    private int hue = 0;
    private long time;

    public MyCallback(int idx) {
        super();
        this.idx = idx;
        TAG = "MyCallback: " + idx;
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
        Log.i(TAG, "onConnectionStateChange");
        gatt.discoverServices();
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        this.gatt = gatt;
        Log.i(TAG, "onServicesDiscovered");
        BluetoothGattService service = gatt.getService(BLUETOOTH_LE_CC254X_SERVICE);
        characteristic = service.getCharacteristic(BLUETOOTH_LE_CC254X_CHAR_RW);
    }

    public void sendColor(int hue) {
        if (characteristic == null) {
            Log.i(TAG, "Waiting for service discovery");
            return;
        }
        // TODO: a queue would be better here so that
        // I know that I'm not overwhelming the writeCharacteristic
        // and should wait until onCharacteristicWrite has finished
        Log.i(TAG, "Color " + hue);
        byte[] val = {
                (byte) hue, (byte) 0xFF, (byte) 0xFF, // pixel 1
                (byte) (hue + 10), (byte) 0xFF, (byte) 0xFF, // pixel 2
                (byte) (hue + 20), (byte) 0xFF, (byte) 0xFF, // pixel 3
                // The remaining 11 bytes are filler
                (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };
        characteristic.setValue(val);
        gatt.writeCharacteristic(characteristic);
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        Log.i(TAG, "onCharacteristicRead");
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        Log.i(TAG, "onCharacteristicWrite");
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        Log.i(TAG, "onCharacteristicChanged");
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorRead(gatt, descriptor, status);
        Log.i(TAG, "onDescriptorRead");
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        Log.i(TAG, "onDescriptorWrite");
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
        super.onMtuChanged(gatt, mtu, status);
        Log.i(TAG, "onMtuChanged");
    }
}
