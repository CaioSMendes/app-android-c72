package com.example.uhf.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.AppData;
import com.example.uhf.R;
import com.example.uhf.activity.LogsActivity;
import com.example.uhf.activity.OperadoresActivity;
import com.example.uhf.activity.RetiradaActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CadastroDetalheActivity extends AppCompatActivity {

    private TextView txtTag;
    private EditText edtObject;
    private EditText edtIdInterno;
    private EditText edtDescription;
    private Button btnCadastrarObject;

    private String baseUrl;
    private String serial;

    private String tagRFID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_detalhe);

        // Inicializa Views
        txtTag = findViewById(R.id.txtTag);
        edtObject = findViewById(R.id.edtObject);
        edtIdInterno = findViewById(R.id.edtIdInterno);
        edtDescription = findViewById(R.id.edtDescription);
        btnCadastrarObject = findViewById(R.id.btnCadastrarObject);

        // Base URL e serial do SharedPreferences
        SharedPreferences prefs = getSharedPreferences("SetupPrefs", MODE_PRIVATE);
        baseUrl = prefs.getString("baseUrl", "");
        serial = prefs.getString("serial", "");

        // Recebe dados da Intent
        tagRFID = getIntent().getStringExtra("tagRFID");
        String object = getIntent().getStringExtra("object");
        String idInterno = getIntent().getStringExtra("idInterno");
        String description = getIntent().getStringExtra("description");

        // Preenche campos
        txtTag.setText(tagRFID);
        edtObject.setText(object != null ? object : "");
        edtIdInterno.setText(idInterno != null ? idInterno : "");
        edtDescription.setText(description != null ? description : "");

        // Botão cadastrar
        btnCadastrarObject.setOnClickListener(v -> enviarObjetoAPI());
    }

    private void enviarObjetoAPI() {
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/api/v1/keylocker_transactions/add_object");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                // Monta JSON
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("locker_serial", serial);

                JSONObject keylockerInfo = new JSONObject();
                keylockerInfo.put("object", edtObject.getText().toString());
                keylockerInfo.put("tagRFID", tagRFID);
                keylockerInfo.put("idInterno", edtIdInterno.getText().toString());
                keylockerInfo.put("description", edtDescription.getText().toString());

                jsonBody.put("keylockerinfo", keylockerInfo);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) response.append(line.trim());
                }

                runOnUiThread(() -> {
                    if (responseCode == 200 || responseCode == 201) {
                        Toast.makeText(CadastroDetalheActivity.this,
                                "Objeto cadastrado com sucesso!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(CadastroDetalheActivity.this,
                                "Erro ao cadastrar objeto: " + responseCode, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(CadastroDetalheActivity.this,
                        "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}