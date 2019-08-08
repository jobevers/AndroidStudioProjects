package com.example.radiobuttontutorial;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Toast;

public class PageTwo extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_page_two);

        Bundle bundle = getIntent().getExtras();
        int nJackets = bundle.getInt("jackets");

        Toast.makeText(this, String.format("We have %d jackets", nJackets), Toast.LENGTH_SHORT).show();

    }
}
