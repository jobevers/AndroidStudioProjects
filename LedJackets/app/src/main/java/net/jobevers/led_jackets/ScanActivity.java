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
    private JacketService jacketService;
    private PatternService patternService;
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
            Log.d(TAG, "GO BUTTON clicked.  Creating jacketService for devices!");
            bindService(new Intent(this, JacketService.class), jacketConnection, Context.BIND_AUTO_CREATE);
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
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause()");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart()");
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        // Both of these services need to run even when the activity
        // is stopped, etc.
        if (jacketService != null) {
            Log.i(TAG, "unbinding from jacket service");
            unbindService(jacketConnection);
            jacketService = null;
        }
        if (patternService != null) {
            Log.i(TAG, "unbinding from pattern service");
            unbindService(patternConnection);
            patternService = null;
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
                PFragment fragment = new PFragment(patternDisplay);
                fragment.setView(processingFrame, ScanActivity.this);
                Log.i(TAG, "PLAY BUTTON clicked.  Creating PatternService for devices!");
                bindService(
                        new Intent(ScanActivity.this, PatternService.class),
                        patternConnection, Context.BIND_AUTO_CREATE);
                // TODO: change to stop, which will a disconnect
                goButton.setEnabled(false);
            });
        }
    };

    // This is kind of dumb, could just make the jacketService a drawListener.
    private PatternService.PatternDrawListener drawListener = new PatternService.PatternDrawListener() {
        @Override
        public void onFrame(int frameCount, HSV[] pixels) {
            jacketService.sendFrame(frameCount, pixels);
        }
    };

    private JacketService.StatusListener childStatusListener;

    @Override
    public void setStatusListener(JacketService.StatusListener statusListener) {
        this.childStatusListener = statusListener;

    }

    /**
     * Defines callbacks for jacketService binding, passed to bindService()
     */
    private ServiceConnection jacketConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "onJacketServiceConnected");
            jacketService = ((JacketService.JacketServiceBinder) binder).getService();
            // Listen to status updates from the jacketService
            jacketService.setStatusListener(statusListener);
            jacketService.connect(devices);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onJacketServiceDisconnected");
            jacketService = null;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "onJacketServiceBindingDied");
        }
    };

    private ServiceConnection patternConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "onPatternServiceConnected");
            patternService = ((PatternService.PatternServiceBinder) binder).getService();
            // create patterns and send the frames back up.
            patternService.addDrawListener(drawListener);
            patternService.run(devices.size());
            jacketService.setPatternService(patternService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onPatternServiceDisconnected");
            patternService = null;
        }
    };
}
