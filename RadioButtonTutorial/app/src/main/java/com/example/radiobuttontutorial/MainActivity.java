package com.example.radiobuttontutorial;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    RadioGroup radioGroup;
    RadioButton radioButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        radioGroup = findViewById(R.id.radioGroup);
    }

    public void onClick(android.view.View view) {
        int radioId = radioGroup.getCheckedRadioButtonId();

        radioButton = findViewById(radioId);

        int idx = radioGroup.indexOfChild(radioButton);

        Intent intent = new Intent(this, PageTwo.class);
        intent.putExtra("jackets", idx + 1);

        startActivity(intent);
    }
}
