package net.jobevers.led_jackets;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class MainActivity extends AppCompatActivity {

    RadioGroup radioGroup;
    RadioButton radioButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_n_jackets);

        radioGroup = findViewById(R.id.radioGroup);
    }

    public void onClick(android.view.View view) {
        int radioId = radioGroup.getCheckedRadioButtonId();

        radioButton = findViewById(radioId);

        int idx = radioGroup.indexOfChild(radioButton);

        Intent intent = new Intent(this, ScanActivity.class);
        intent.putExtra("jackets", idx + 1);

        startActivity(intent);
    }
}
