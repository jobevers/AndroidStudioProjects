package net.jobevers.colorchangingrectangle;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    View colorBox;
    int hue = 0;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        colorBox = findViewById(R.id.colorBox);

        mHandler = new Handler();
        mColorSetter.run();
    }

    Runnable mColorSetter = new Runnable() {
        @Override
        public void run() {
            try {
                float[] hsv = {hue, 1, 1};
                colorBox.setBackgroundColor(Color.HSVToColor(hsv));
                hue = (hue + 1) % 360;
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                mHandler.postDelayed(mColorSetter, 30);
            }
        }
    };
}
