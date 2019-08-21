package net.jobevers.led_jackets;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.ColorInt;
import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeUnit;

import processing.core.PApplet;

public class PatternDisplay extends PApplet {

    String TAG = "PatternDisplay";
    long lastTime = 0;
    int hue = 0;

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }


    // https://processing.org/reference/settings_.html
    // settings is only needed because I'm not in the
    // processing IDE.
    public void settings() {
        size(100, 100);
    }

    public void setup() {
        frameRate(30);
        colorMode(HSB, 255, 255, 255);
    }

    public void draw() {
        long start = System.currentTimeMillis();
        // Idea will be that the pattern service will publish HSV frames to me
        // and I'll draw them in squares.
        long end = System.currentTimeMillis();
        if (end - start > 33) {
            Log.w(TAG, "Frame took: " + (end - start) + "ms. It should be <33ms");
        }
    }
}