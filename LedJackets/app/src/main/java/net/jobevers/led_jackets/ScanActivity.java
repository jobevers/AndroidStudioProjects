package net.jobevers.led_jackets;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.ColorInt;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import processing.android.PFragment;

public class ScanActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener, DevicesFragment.BleScanCompletedListener {

    private String TAG = "ScanActivity";
    private Button goButton;
    private List<BluetoothDevice> devices;
    private JacketService service;
    private PatternDisplay patternDisplay;
    FrameLayout processingFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        processingFrame = findViewById(R.id.processing_frame);
        goButton = findViewById(R.id.go_button);
        goButton.setEnabled(false);
        goButton.setOnClickListener((View v) -> {
            Log.d(TAG, "GO BUTTON clicked.  Creating service for devices!");
            bindService(new Intent(this, JacketService.class), connection, Context.BIND_AUTO_CREATE);
            // Now, wait until we're all connected
            goButton.setEnabled(false);
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
        if (service != null) {
            unbindService(connection);
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


    private JacketService.StatusListener statusListener = new JacketService.StatusListener() {
        @Override
        public void setStatus(BluetoothDevice device, String status) {
            childStatusListener.setStatus(device, status);
        }

        @Override
        public void onConnect() {
            goButton.setText(R.string.play);
            goButton.setEnabled(true);
            goButton.setOnClickListener((View v) -> {
                Log.i(TAG, "EVERYTHING IS CONNECTED");
                // This code is from the example at
                // https://android.processing.org/tutorials/android_studio/index.html
                patternDisplay = new PatternDisplay();
                patternDisplay.setDrawListener(drawListener);
                PFragment fragment = new PFragment(patternDisplay);
                fragment.setView(processingFrame, ScanActivity.this);
            });
        }
    };

    // This is kind of dumb, could just make the service a drawListener.
    private PatternDisplay.PatternDrawListener drawListener = new PatternDisplay.PatternDrawListener() {
        @Override
        public void onFrame(int frameCount, int[] pixels) {
            service.sendFrame(frameCount, pixels);
        }
    };

    private JacketService.StatusListener childStatusListener;

    @Override
    public void setStatusListener(JacketService.StatusListener statusListener) {
        this.childStatusListener = statusListener;

    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "onServiceConnected");
            service = ((JacketService.JacketServiceBinder) binder).getService();
            // Listen to status updates from the service
            service.setStatusListener(statusListener);
            service.connect(devices);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            service = null;
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

    void sendTestData() {
        // TODO: will actually want to start processing
        // and let it send frames, but I just want... something
        @ColorInt int[] colors = {
                Color.RED, Color.RED, Color.RED, Color.RED, Color.RED,
                Color.RED, Color.RED, Color.RED, Color.RED, Color.RED,
                Color.RED, Color.RED, Color.RED, Color.RED, Color.RED,
                Color.RED, Color.RED, Color.RED, Color.RED, Color.RED,
                Color.RED, Color.RED, Color.RED, Color.RED, Color.RED,
        };
        try {
            service.sendFrame(1, colors);
            TimeUnit.MILLISECONDS.sleep(1000);
            service.sendFrame(30, colors);
            TimeUnit.MILLISECONDS.sleep(1000);
            service.sendFrame(60, colors);
            TimeUnit.MILLISECONDS.sleep(1000);
            service.sendFrame(90, colors);
            TimeUnit.MILLISECONDS.sleep(1000);
            for (int i = 0; i < 25; i++) {
                colors[i] = Color.GREEN;
            }
            service.sendFrame(120, colors);
            TimeUnit.MILLISECONDS.sleep(1000);
            service.sendFrame(150, colors);
            TimeUnit.MILLISECONDS.sleep(1000);
        } catch (Exception ex) {

        }
    }
}
