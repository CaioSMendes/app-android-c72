package com.example.uhf.activity;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import java.util.ArrayList;

public class LogsActivity extends AppCompatActivity {

    private ListView lvLogs;
    private ArrayList<String> logs;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        lvLogs = findViewById(R.id.lvLogs);

        // Recebe logs via Intent
        logs = getIntent().getStringArrayListExtra("logs");
        if (logs == null) {
            logs = new ArrayList<>();
        }

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                logs);
        lvLogs.setAdapter(adapter);
    }
}
