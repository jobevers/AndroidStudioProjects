package net.jobevers.led_jackets;

import android.bluetooth.BluetoothDevice;

import java.util.List;

import processing.core.PApplet;

public class PatternGenerator extends PApplet {

    PatternDrawListener drawListener;
    int hue = 0;

    public interface PatternDrawListener {
        void onFrame(int hue);
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
        loadPixels();
        for (int i=0; i<pixels.length; i++) {
            pixels[i] = color(hue, 255, 255);
        }
        updatePixels();
        drawListener.onFrame(hue);
        hue = (hue + 2) % 256;
    }
}