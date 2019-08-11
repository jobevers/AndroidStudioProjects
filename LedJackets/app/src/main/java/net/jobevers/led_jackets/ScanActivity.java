package net.jobevers.led_jackets;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

public class ScanActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener, DevicesFragment.BleScanCompletedListener {

    private Button goButton;
    private List<BluetoothDevice> devices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        goButton = findViewById(R.id.go_button);
        goButton.setEnabled(false);
        goButton.setOnClickListener((View v) -> {
            Context context = v.getContext();
            Intent intent = new Intent(context, ColorBox.class);
            ScanActivity activity = (ScanActivity) context;
            // The button is only enabled once we have devices.
            // so we know that this will exists
            intent.putExtra("nDevices", activity.devices.size());
            for (int i = 0; i < activity.devices.size(); i++) {
                BluetoothDevice device = activity.devices.get(i);
                intent.putExtra("device" + i, device.getAddress());
            }
            startActivity(intent);
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
}
