package net.jobevers.led_jackets;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.ColorInt;
import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeUnit;

import processing.core.PApplet;

public class PatternGenerator extends PApplet {

    String TAG = "PatternGenerator";
    long lastTime = 0;
    PatternDrawListener drawListener;
    int hue = 0;

    public interface PatternDrawListener {
        void onFrame(int frameCount, @ColorInt int[] pixels);
    }

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

    public void setDrawListener(PatternDrawListener drawListener) {
        this.drawListener = drawListener;
    }

    public void draw() {
        long now = System.currentTimeMillis();
        long diff = now - lastTime;
        double rate = 1000.0 / diff;
        lastTime = now;
        @ColorInt int c = color(hue, 255, 255);
        loadPixels();
        for (int i=0; i<pixels.length; i++) {
            pixels[i] = c;
        }
        updatePixels();
        drawListener.onFrame(frameCount, pixels);
        hue = (hue + 2) % 256;
        long end = System.currentTimeMillis();
        if (end - now > 33) {
            Log.w(TAG, "Frame took: " + (end - now) + "ms. It should be <33ms");
        }
    }
}