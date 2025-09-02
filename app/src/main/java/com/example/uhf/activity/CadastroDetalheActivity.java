package com.example.uhf.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CadastroDetalheActivity extends AppCompatActivity {

    private TextView txtTag;
    private EditText edtObject, edtIdInterno, edtDescription;
    private Button btnCadastrarObject;

    private String baseUrl;
    private String serial;
    private String tagRFID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_detalhe);

        inicializarViews();
        carregarConfiguracoes();
        preencherCampos();
        configurarBotao();
    }

    private void inicializarViews() {
        txtTag = findViewById(R.id.txtTag);
        edtObject = findViewById(R.id.edtObject);
        edtIdInterno = findViewById(R.id.edtIdInterno);
        edtDescription = findViewById(R.id.edtDescription);
        btnCadastrarObject = findViewById(R.id.btnCadastrarObject);
    }

    private void carregarConfiguracoes() {
        SharedPreferences prefs = getSharedPreferences("SetupPrefs", MODE_PRIVATE);
        baseUrl = prefs.getString("baseUrl", "");
        serial = prefs.getString("serial", "");
    }

    private void preencherCampos() {
        tagRFID = getIntent().getStringExtra("tagRFID");
        String object = getIntent().getStringExtra("object");
        String idInterno = getIntent().getStringExtra("idInterno");
        String description = getIntent().getStringExtra("description");

        txtTag.setText(tagRFID != null ? tagRFID : "");
        edtObject.setText(object != null ? object : "");
        edtIdInterno.setText(idInterno != null ? idInterno : "");
        edtDescription.setText(description != null ? description : "");
    }

    private void configurarBotao() {
        btnCadastrarObject.setOnClickListener(v -> {
            // Validação dos campos obrigatórios
            String object = edtObject.getText().toString().trim();
            String idInterno = edtIdInterno.getText().toString().trim();
            String description = edtDescription.getText().toString().trim();

            if (tagRFID == null || tagRFID.isEmpty()) {
                Toast.makeText(this, "Tag RFID inválida!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (object.isEmpty()) {
                Toast.makeText(this, "Preencha o campo Objeto!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (idInterno.isEmpty()) {
                Toast.makeText(this, "Preencha o campo ID Interno!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (description.isEmpty()) {
                Toast.makeText(this, "Preencha o campo Descrição!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Se passou na validação, envia para API
            cadastrarObjeto(tagRFID, object, idInterno, description);
        });
    }

    private void cadastrarObjeto(String tag, String object, String idInterno, String description) {
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/api/v1/keylocker_transactions/add_object");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("locker_serial", serial);

                JSONObject keylockerInfo = new JSONObject();
                keylockerInfo.put("object", object);
                keylockerInfo.put("tagRFID", tag);
                keylockerInfo.put("idInterno", idInterno);
                keylockerInfo.put("description", description);

                jsonBody.put("keylockerinfo", keylockerInfo);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes("utf-8"));
                }

                int responseCode = conn.getResponseCode();
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        responseCode < 400 ? conn.getInputStream() : conn.getErrorStream(), "utf-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) response.append(line.trim());
                }
                conn.disconnect();

                runOnUiThread(() -> {
                    try {
                        if (responseCode == 200 || responseCode == 201) {
                            // Mostra um diálogo de sucesso
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Sucesso")
                                    .setMessage("Objeto cadastrado com sucesso!")
                                    .setCancelable(false)
                                    .setPositiveButton("OK", (dialog, which) -> {
                                        dialog.dismiss(); // fecha o diálogo
                                        finish(); // fecha a activity e volta para a lista de leitura
                                    })
                                    .show();

                        } else {
                            String mensagemErro = response.toString();
                            try {
                                JSONObject json = new JSONObject(response.toString());
                                if (json.has("error")) {
                                    mensagemErro = json.getString("error");
                                }
                            } catch (Exception e) {
                                Log.e("CadastroDetalhe", "Erro ao interpretar JSON de erro", e);
                            }

                            Toast.makeText(this,
                                    "Erro ao cadastrar objeto: " + mensagemErro, Toast.LENGTH_LONG).show();
                            Log.e("CadastroDetalhe", "Resposta API: " + response.toString());
                        }
                    } catch (Exception e) {
                        Toast.makeText(this,
                                "Erro inesperado ao processar resposta", Toast.LENGTH_LONG).show();
                        Log.e("CadastroDetalhe", "Erro inesperado", e);
                    }
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show());
                Log.e("CadastroDetalhe", "Erro ao cadastrar objeto", e);
            }
        }).start();
    }
}