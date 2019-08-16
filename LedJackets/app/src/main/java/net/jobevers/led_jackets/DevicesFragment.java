package net.jobevers.led_jackets;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * show list of BLE devices
 */


public class DevicesFragment extends ListFragment {

    BleScanCompletedListener bleScanCompletedListener;

    public interface BleScanCompletedListener {
        void onBleScanCompleted(List<BluetoothDevice> devices);
        void setStatusListener(JacketService.StatusListener listener);
    }

    class DeviceWithStatus {
        public BluetoothDevice device;
        public String status;
        DeviceWithStatus(BluetoothDevice device, String status){
            this.device = device;
            this.status = status;
        }
    }

    private String TAG="DevicesFragment";

    private Menu menu;
    private final BluetoothAdapter bluetoothAdapter;
    private BroadcastReceiver bleDiscoveryBroadcastReceiver;
    private IntentFilter bleDiscoveryIntentFilter;
    private boolean francineFound = false;

    private ArrayList<DeviceWithStatus> listItems = new ArrayList<>();
    private ArrayAdapter<DeviceWithStatus> listAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scancallback;
    private boolean stopped = true;
    int nJackets;


    public DevicesFragment() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        bleDiscoveryBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    Log.i(TAG, "onReceive: devicetype: " + device.getType());
                    Log.i(TAG, "name: " + device.getName());
                    if (device.getName().startsWith("FRANCINE")) {
                        francineFound = true;
                    }
                    if(device.getType() != BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                        Log.i(TAG, "updating scan");
                        getActivity().runOnUiThread(() -> updateScan(device));
                    }
                }
                if(intent.getAction().equals((BluetoothAdapter.ACTION_DISCOVERY_FINISHED))) {
                    if (francineFound){
                        stopScan();
                    }
                }
            }
        };
        bleDiscoveryIntentFilter = new IntentFilter();
        bleDiscoveryIntentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        bleDiscoveryIntentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        nJackets = getArguments().getInt("jackets");
        listAdapter = new ArrayAdapter<DeviceWithStatus>(getActivity(), 0, listItems) {
            @Override
            public View getView(int position, View view, ViewGroup parent) {
                DeviceWithStatus ds = listItems.get(position);
                BluetoothDevice device = ds.device;
                if (view == null)
                    view = getActivity().getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                TextView text1 = view.findViewById(R.id.textName);
                TextView text2 = view.findViewById(R.id.textAddress);
                TextView text3 = view.findViewById(R.id.textStatus);
                if(device.getName() == null || device.getName().isEmpty())
                    text1.setText("<unnamed>");
                else
                    text1.setText(device.getName());
                text2.setText(device.getAddress());
                text3.setText(ds.status);
                return view;
            }
        };

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter(null);
        View header = getActivity().getLayoutInflater().inflate(R.layout.device_list_header, null, false);
        getListView().addHeaderView(header, null, false);
        setEmptyText("initializing...");
        ((TextView) getListView().getEmptyView()).setTextSize(18);
        setListAdapter(listAdapter);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_devices, menu);
        this.menu = menu;
        if(!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
            menu.findItem(R.id.bt_settings).setEnabled(false);
        if(bluetoothAdapter==null || !getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            menu.findItem(R.id.ble_scan).setEnabled(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        // getActivity().registerReceiver(bleDiscoveryBroadcastReceiver, bleDiscoveryIntentFilter);
        if(bluetoothAdapter == null || !getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            setEmptyText("<bluetooth LE not supported>");
        else if(!bluetoothAdapter.isEnabled())
            setEmptyText("<bluetooth is disabled>");
        else
            setEmptyText("<use SCAN to refresh devices>");
    }

    @Override
    public void onPause() {
        super.onPause();
        stopScan();
        //getActivity().unregisterReceiver(bleDiscoveryBroadcastReceiver);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.ble_scan) {
            startScan();
            return true;
        } else if (id == R.id.ble_scan_stop) {
            stopScan();
            return true;
        } else if (id == R.id.bt_settings) {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void startScan() {
        if (!stopped) {
            Log.i(TAG, "Previous scan must be stopped first");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(getText(R.string.location_permission_title));
                builder.setMessage(getText(R.string.location_permission_message));
                builder.setPositiveButton(android.R.string.ok,
                        (dialog, which) -> requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0));
                builder.show();
                return;
            }
        }
        listItems.clear();
        listAdapter.notifyDataSetChanged();
        setEmptyText("<scanning...>");
        menu.findItem(R.id.ble_scan).setVisible(false);
        menu.findItem(R.id.ble_scan_stop).setVisible(true);

        ScanSettings settings = new ScanSettings.Builder().build();

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setDeviceName("FRANCINE1").build());
        filters.add(new ScanFilter.Builder().setDeviceName("FRANCINE2").build());
        filters.add(new ScanFilter.Builder().setDeviceName("FRANCINE3").build());
        filters.add(new ScanFilter.Builder().setDeviceName("FRANCINE4").build());

        HashSet<String> foundDevices = new HashSet<>();

        Log.i(TAG, "Starting Scan");
        scancallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                Log.i(TAG, "Name: " + device.getName());
                if (!foundDevices.contains(device.getName())) {
                    Log.i(TAG, "updating scan");
                    getActivity().runOnUiThread(() -> updateScan(device));
                    foundDevices.add(device.getName());
                    if (foundDevices.size() >= nJackets) {
                        stopScan();
                    }
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
                Log.i(TAG, "onBatchScanResults");
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.i(TAG, "scanFailed: " + errorCode);
            }
        };
        stopped = false;
        bluetoothLeScanner.startScan(filters, settings, scancallback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        // ignore requestCode as there is only one in this fragment
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            new Handler(Looper.getMainLooper()).postDelayed(this::startScan,1); // run after onResume to avoid wrong empty-text
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getText(R.string.location_denied_title));
            builder.setMessage(getText(R.string.location_denied_message));
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            bleScanCompletedListener = (BleScanCompletedListener) context;
            bleScanCompletedListener.setStatusListener(statusListener);
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() +  " must implement BleScanCompletedListener");
        }
    }

    JacketService.StatusListener statusListener = new JacketService.StatusListener() {
        @Override
        public void setStatus(BluetoothDevice device, String status) {
            Log.i(TAG, "Recieved " + status);
            for (DeviceWithStatus ds: listItems) {
                if (ds.device == device) {
                    ds.status = status;
                }
            }
            listAdapter.notifyDataSetChanged();
        }
    };

    private void updateScan(BluetoothDevice device) {
        Log.i(TAG, "Device Name: " + device.getName());

        if(!containsDevice(device)) {
            listItems.add(new DeviceWithStatus(device, "DISCONNECTED"));
            Collections.sort(listItems, DevicesFragment::compareTo);
            listAdapter.notifyDataSetChanged();
        }
    }

    private boolean containsDevice(BluetoothDevice device) {
        for (DeviceWithStatus dev: listItems) {
            if (dev.device == device) {
                return true;
            }
        }
        return false;
    }

    private void stopScan() {
        setEmptyText("<no bluetooth devices found>");
        if(menu != null) {
            menu.findItem(R.id.ble_scan).setVisible(true);
            menu.findItem(R.id.ble_scan_stop).setVisible(false);
        }
        Log.i(TAG, "Stopping scan");
        bluetoothLeScanner.stopScan(scancallback);
        stopped = true;

        bleScanCompletedListener.onBleScanCompleted(getDevices());
        //bluetoothAdapter.cancelDiscovery();
    }

    private List<BluetoothDevice> getDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        for (DeviceWithStatus ds : this.listItems) {
            devices.add(ds.device);
        }
        return devices;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
    }

    /**
     * sort by name, then address. sort named devices first
     */
    static int compareTo(DeviceWithStatus da, DeviceWithStatus db) {
        BluetoothDevice a = da.device;
        BluetoothDevice b = db.device;
        boolean aValid = a.getName()!=null && !a.getName().isEmpty();
        boolean bValid = b.getName()!=null && !b.getName().isEmpty();
        if(aValid && bValid) {
            int ret = a.getName().compareTo(b.getName());
            if (ret != 0) return ret;
            return a.getAddress().compareTo(b.getAddress());
        }
        if(aValid) return -1;
        if(bValid) return +1;
        return a.getAddress().compareTo(b.getAddress());
    }
}
