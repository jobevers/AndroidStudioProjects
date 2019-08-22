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
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JacketService extends Service {
    private final String CONNECTED = "CONNECTED";
    // CCCD = client characteristic configuration
    private static final UUID BLUETOOTH_LE_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_CC254X_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_CC254X_CHAR_RW = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private static final int N_LEDS = 25;
    // In a message, how many bytes are for the header
    // The header is two bytes:
    // First 4 bits are MSG_NUM {0,1}
    // The next 4 bits are the frame increment (to speed up slow jackets)
    // The second & third byte is the frame count
    private static final int HEADER_SIZE = 3;

    private String TAG = "JacketService";
    private List<BluetoothDevice> devices;
    private ArrayList<MyGattCallback> callbacks = new ArrayList<>();
    private StatusListener listener;
    // Use this to post stuff back to the UI
    private final Handler mainLooper;
    private PatternService patternService;

    class JacketServiceBinder extends Binder {
        JacketService getService() {
            return JacketService.this;
        }
    }

    interface StatusListener {
        void setStatus(BluetoothDevice device, String status);
        void onConnect();
    }

    private final IBinder binder;

    public JacketService() {
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new JacketServiceBinder();
    }

    public void setPatternService(PatternService ps) {
        patternService = ps;
    }

    @Override
    public void onDestroy() {
        for (MyGattCallback cb : callbacks) {
            cb.disposeHandler();
        }
    }

    public void connect(List<BluetoothDevice> devices) {
        Log.d(TAG, "Connect");
        this.devices = devices;
        // need to set the listener before connecting
        for (int i = 0; i < devices.size(); i++) {
            BluetoothDevice dev = devices.get(i);
            MyGattCallback callback = new MyGattCallback(i, 0);
            callbacks.add(callback);
            dev.connectGatt(this, false, callback);
        }
    }

    // TODO: add in a delay
    private void reconnect(int idx) {
        mainLooper.post(() -> {
            Log.i(TAG, "Attempting to reconnect on device: " + idx);
            BluetoothDevice dev = devices.get(idx);
            int version = callbacks.get(idx).version;
            MyGattCallback callback = new MyGattCallback(idx, version + 1);
            callbacks.set(idx, callback);
            dev.connectGatt(this, false, callback);
        });
    }

    public void setStatusListener(StatusListener listener) {
        this.listener = listener;
    }

    private void setStatus(int idx, String status) {
        BluetoothDevice dev = devices.get(idx);
        mainLooper.post(() -> {
            listener.setStatus(dev, status);
        });
        if (status == CONNECTED) {
            if (allDevicesConnected()) {
                mainLooper.post(() -> {
                    listener.onConnect();
                });
            }
        }
    }

    private void resetPattern(HSV color) {
        mainLooper.post(() -> {
            patternService.triggerReset();
            for (MyGattCallback cb: callbacks) {
                cb.sendReset(color);
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public boolean allDevicesConnected() {
        for (MyGattCallback cb : callbacks) {
            if (!cb.isConnected()) {
                Log.i(TAG, "Callback is disconnected: " + cb.TAG);
                return false;
            }
        }
        Log.i(TAG, "EVERYTHING IS CONNECTED");
        return true;
    }

    public void sendFrame(int frame, HSV[] colors) {
        if (frame > 100) {
            // TODO: this is just temporary for testing
            resetPattern(new HSV(255, 255, 255));
        } else {
            for (int i = 0; i < callbacks.size(); i++) {
                MyGattCallback cb = callbacks.get(i);
                // TODO: actually send frames per jacket
                cb.sendFrame(frame, Arrays.copyOfRange(colors, 0, 25));
            }
        }
    }

    // This is a very thorough implementation of these callbacks
    // https://github.com/RatioLabs/BLEService/blob/master/DeviceService/src/com/ratio/deviceService/BTLEDeviceManager.java#L490
    // and this seems decent as well:
    // https://intersog.com/blog/tech-tips/how-to-work-properly-with-bt-le-on-android/
    // Of note is that there is NO queue for the commands or callbacks
    // so need to make some effort to not block the thread the callbacks
    // are called on.
    // I'm currently only sending data, waiting for a response, sending data
    // etc. so I should be okay.
    class MyGattCallback extends BluetoothGattCallback implements Handler.Callback {
        private String TAG;
        private int idx;
        private int version;

        private int payloadSize = 20;
        BluetoothGattCharacteristic characteristic;
        BluetoothGatt gatt;
        private boolean connected = false;
        private boolean wait = true;
        // generally want to wait until we get a confirmation message before sending
        // the next frame.  But if its been too long and were still connected, try again.
        private long waitOverride = Long.MAX_VALUE;
        // keep track of consecutive failures.  If this gets too high we'll disconnect
        // to force a reconnect;
        private int failures = 0;
        // Each frame consists of 2 messages.  This gets set to 1 after we send the first message
        //private int nextMessage;
        // set to true if the connection handler should close when we disconnect
        // otherwise we'll try to reconnect.
        private boolean closeOnDisconnect = false;

        private ArrayDeque<byte[]> messages = new ArrayDeque<>();
//        private byte[] message0 = new byte[payloadSize];
//        private byte[] message1 = new byte[payloadSize];
        Handler bleHandler;


        MyGattCallback(int idx, int version) {
            this.idx = idx;
            this.version = version;
            TAG = "MyGattCallback-" + idx + ":" + version;
            // Want all actual work to happen using a handler thread so that
            // The BLE response is fast.
            HandlerThread handlerThread = new HandlerThread("BLE-Worker");
            handlerThread.start();
            bleHandler = new Handler(handlerThread.getLooper(), this);
        }

        private void disconnectAndClose() {
            Log.d(TAG, "disconnectAndClose()");
            closeOnDisconnect = true;
            // TODO: trigger a timer here just in-case
            //       nothing happens in onConnectionStateChange
            gatt.disconnect();
        }

        public void disposeHandler() {
            Log.d(TAG, "disposeHandler()");
            bleHandler.removeCallbacksAndMessages(null);
            bleHandler.getLooper().quit();
        }
        final int MSG_DISCOVER_SERVICES = 0;
        final int MSG_ERROR_STATUS = 1;
        final int MSG_READ = 2;
        final int MSG_ENQUEUE = 3;
        final int MSG_REPLACE = 4;
        final int MSG_SEND = 5;

        public boolean isConnected() {
            return connected;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i(TAG, "onConnectionStateChange status = " + status + " state = " + newState);
            characteristic = null;
            connected = false;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // if there was a status error, DO NOT EXECUTE ANYMORE COMMANDS ON THIS DEVICE, and disconnect from it.
                bleHandler.obtainMessage(MSG_ERROR_STATUS, gatt).sendToTarget();
            } else {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    bleHandler.obtainMessage(MSG_DISCOVER_SERVICES, gatt).sendToTarget();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (closeOnDisconnect) {
                        setStatus(idx, "CLOSING");
                        gatt.close();
                        disposeHandler();
                    } else {
                        setStatus(idx, "DISCONNECTED");
                        // Lets.. try to re-connect, I guess.
                        // TODO: add in a retry count
                        // TODO: add in a timer on connect because sometimes it just seems to fail
                        waitOverride = Integer.MAX_VALUE;
                        Log.i(TAG, "calling gatt.connect()");
                        gatt.connect();
                    }
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
                    setStatus(idx, CONNECTED);
                    //parent.onConnection(this.idx);
                    Log.d(TAG, "connected");
                }
            } else {
                Log.d(TAG, "unknown write descriptor finished, status=" + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.i(TAG, "onCharacteristicWrite");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte[] val = characteristic.getValue();
            bleHandler.obtainMessage(MSG_READ, val).sendToTarget();
        }

        private final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

        private String bytesToHex(byte[] bytes) {
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = HEX_ARRAY[v >>> 4];
                hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        }
        int lastFrame = 0;

        public void sendFrame(int frame, HSV[] colors) {
            assert colors.length == N_LEDS;
            if (!connected) {
                Log.d(TAG, "Waiting to be connected");
                return;
            }
            boolean shouldExit = false;
            if (wait) {
                // TODO: waiting is now expected behavior so its not worth logging when this happens
                // unless its been a LONG time since we last sent a message.
                return;
            }
            if (characteristic == null) {
                // Shouldn't every get here, connected is set after characteristic.
                Log.i(TAG, "Waiting for service discovery");
                failures += 1;
                shouldExit = true;
            }
            // TODO: really rethink what is a "failure"....
            if (failures > 30) {
                if (gatt != null) {
                    Log.i(TAG, "We have had too many failures, lets disconnect");
                    gatt.disconnect();
                    connected = false;
                }
                return;
            }
            if (shouldExit) {
                return;
            }
            failures = 0;
            Log.i(TAG, "Sending Frame " + frame);
            if (lastFrame > frame) {
                Log.w(TAG, "FRAME NUMBER IS GOING BACKWARDS " + lastFrame + ">" + frame);
            }
            lastFrame = frame;
            queueMessage(frame, colors, 0);
            queueMessage(frame, colors, 1);
            sendMessage();
        }

        private void queueMessage(int frame, HSV[] colors, int offset) {
            byte[] message = new byte[payloadSize];
            // First 4 bits are the message type, the last four bits are unused
            message[0] = (byte) ((offset << 4) | 0x01);
            // TODO: if frame > 0xFFFF (65,535) then we should error or something so that
            //       we know to restart
            message[1] = (byte) ((frame & 0xFF00) >> 8);
            message[2] = (byte) (frame & 0x00FF);
            // First message contains the even pixels (0, 2, 4..)
            // Second message contains the odd pixels (1, 3, 5...)
            // For some reason I convinced myself that interlaced is better
            // But in the way the arduino is coded right now I wait
            // for the entire frame to be received, so it doesn't really matter.
            // Maybe some point in the future I could be more clever....
            for (int i = 0; (2 * i + offset) < N_LEDS; i++) {
                HSV hsv = colors[i];
                message[i + HEADER_SIZE] = hsv.pack();
            }
            bleHandler.obtainMessage(MSG_ENQUEUE, message).sendToTarget();
        }

        private void sendReset(HSV color) {
            Log.i(TAG, "SENDING RESET MSG");
            // part of being reset is the the frame number is reset to zero
            lastFrame = 0;
            byte[] data = new byte[20];
            data[0] = (byte) ((2 << 4) | 0x00);
            data[1] = (byte)0x00;
            data[2] = (byte)0x00;
            data[3] = (byte)color.hue;
            data[4] = (byte)color.saturation;
            data[5] = (byte)color.value;
            bleHandler.obtainMessage(MSG_REPLACE, data).sendToTarget();
            if (!wait) {
                bleHandler.obtainMessage(MSG_SEND, null).sendToTarget();
            }
        }

        private void sendMessage() {
            bleHandler.obtainMessage(MSG_SEND, null).sendToTarget();
        }

        @Override
        public boolean handleMessage(Message msg) {
            Log.d(TAG, "handleMessage()");
            switch (msg.what) {
                case MSG_DISCOVER_SERVICES: {
                    BluetoothGatt gatt = ((BluetoothGatt) msg.obj);
                    Log.i(TAG, "calling discoverServices()");
                    // Reset the failure count so that we don't accidentally disconnect right after connecting
                    failures = 0;
                    gatt.discoverServices();
                    setStatus(idx, "DISCOVERING SERVICES");
                    return true;
                }
                case MSG_ERROR_STATUS: {
                    BluetoothGatt gatt = ((BluetoothGatt) msg.obj);
                    // Is there a time I would close without
                    // disposing the handler?
                    gatt.close();
                    disposeHandler();
                    setStatus(idx, "ERROR");
                    if (!closeOnDisconnect) {
                        reconnect(this.idx);
                    }
                    return true;
                }
                case MSG_READ: {
                    byte[] val = ((byte[]) msg.obj);
                    Log.i(TAG, "onCharacteristicChanged: " + bytesToHex(val));
                    // TODO: the first byte is the message type.
                    // TODO: the next two bytes are the frame number that the arduino is on
                    // javas lack of an unsigned byte is SO annoying
                    int frameNum = ((val[1] & 0xFF) << 8) | (val[2] & 0xFF);
                    Log.i(TAG, "Received frame " + frameNum);
                    if (frameNum < 0) {
                        Log.w(TAG, "frameNum IS NEGATIVE: " + frameNum + ", " + Integer.toHexString(frameNum));
                    }
                    // TODO: the 4th byte is a 1 or a zero
                    if (val[3] == 1) {
                        // this means that there was an error on the android end. Either the buffer was full
                        // or a message was received out of order.
                        // So, this means that we need to reset and NOT send message1.
                        Log.i(TAG, "Error on arduino. Resetting to message0");
                        messages.clear();
                    }
                    if (messages.size() == 0) {
                        // Turn off wait because we got a response and can now get the next frame
                        wait = false;
                    } else {
                        sendMessage();
                    }
                    // TODO: actually check the response to see if it was for the right message
                    // and if we're supposed to allow the follow up message.
                    return true;
                }
                case MSG_ENQUEUE: {
                    byte[] val = ((byte[]) msg.obj);
                    messages.add(val);
                    return true;
                }
                case MSG_REPLACE: {
                    byte[] val = ((byte[]) msg.obj);
                    messages.clear();
                    messages.add(val);
                    return true;
                }
                case MSG_SEND: {
                    byte[] data = messages.remove();
                    Log.i(TAG, "Writing msg: " + bytesToHex(data));
                    wait = true;
                    waitOverride = System.currentTimeMillis() + 1000;
                    characteristic.setValue(data);
                    gatt.writeCharacteristic(characteristic);
                }
            }
            return false;
        }
    }
}
