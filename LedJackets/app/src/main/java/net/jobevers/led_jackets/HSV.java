package net.jobevers.led_jackets;

import android.support.annotation.ColorInt;

public class HSV {
    int hue;
    int saturation;
    int value;

    public HSV(int hue, int saturation, int value) {
        this.hue = hue;
        this.saturation = saturation;
        this.value = value;
    }

    public @ColorInt int toColor() {
        float hsv[] = {
                (float)(hue * 360.0 / 255),
                (float)(saturation / 255.0),
                (float)(value / 255.0)
        };
        return android.graphics.Color.HSVToColor(hsv);
    }

    public byte pack() {
        // Grab 5 most significant bits of the hue
        // The 1 most significant bits of the sat
        // The 2 most significant bits of val
        // The latter two need to be shifted 4 and 6 bits respectively
        // The end result is 8 bits: hhhhhssv.
        // Note that >>> is UNSIGNED shift.
        return (byte) (
                (this.hue & 0xF8)                   // 0xF8 = 11111000
                | ((this.saturation & 0xC0) >>> 5)  // 0xC0 = 11000000
                | ((this.value & 0x80) >>> 7)       // 0x80 = 10000000
        );
    }

}
