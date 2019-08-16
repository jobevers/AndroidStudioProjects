package net.jobevers.led_jackets;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

public class ScanActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener, DevicesFragment.BleScanCompletedListener {

    private String TAG = "ScanActivity";
    private Button goButton;
    private List<BluetoothDevice> devices;
    private JacketService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        goButton = findViewById(R.id.go_button);
        goButton.setEnabled(false);
        goButton.setOnClickListener((View v) -> {
            Log.d(TAG, "GO BUTTON clicked.  Creating service for devices!");
            bindService(new Intent(this, JacketService.class), connection, Context.BIND_AUTO_CREATE);
        });
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        if (savedInstanceState == null) {
            // Pass through the bundle.
            Bundle bundle = getIntent().getExtras();
            DevicesFragment fragObj = new DevicesFragment();
            fragObj.setArguments(bundle);
            getSupportFragmentManager().beginTransaction().add(R.id.fragment, fragObj, "devices").commit();
        } else {
            onBackStackChanged();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount() > 0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBleScanCompleted(List<BluetoothDevice> devices) {
        this.devices = devices;
        goButton.setEnabled(true);
    }

    private JacketService.StatusListener statusListener;

    @Override
    public void setStatusListener(JacketService.StatusListener statusListener) {
        this.statusListener = statusListener;
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "onServiceConnected");
            service = ((JacketService.JacketServiceBinder) binder).getService();
            // pass through the listener from the DeviceFragment
            service.setStatusListener(statusListener);
            service.connect(devices);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "onBindingDied");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.d(TAG, "onNullBinding");
        }
    };
}
