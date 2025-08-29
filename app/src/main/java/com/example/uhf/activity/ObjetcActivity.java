package com.example.uhf.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import org.json.JSONObject;

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

public class ObjetcActivity extends AppCompatActivity {

    private TextView txtDadosRetirada;
    private TextView txtDebug;
    private ListView lvEpcs;
    private Button btnChecar;

    private Button btnObjeto;

    private Button btnRetirada;

    private ArrayList<String> listaTagsSucesso = new ArrayList<>();

    private ArrayList<String> logsRequests = new ArrayList<>();
    private ArrayList<String> listaEpcs;
    private ArrayList<String> listaResultados = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private String baseUrl;
    private String serial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retirada);

        // Referências da tela
        btnRetirada = findViewById(R.id.btnRetirada);
        btnRetirada.setVisibility(View.GONE); // opcional, já está gone no XML

        txtDadosRetirada = findViewById(R.id.txtDadosRetirada);
        txtDebug = findViewById(R.id.txtDebug);
        lvEpcs = findViewById(R.id.lvEpcs);
        btnChecar = findViewById(R.id.btnChecar);
        btnObjeto = findViewById(R.id.btnCadastro);


        // Base URL e serial do SharedPreferences
        SharedPreferences prefs = getSharedPreferences("SetupPrefs", MODE_PRIVATE);
        baseUrl = prefs.getString("baseUrl", "");
        serial = prefs.getString("serial", "");
        String email = prefs.getString("email", "");
        String senha = prefs.getString("senha", "");

        // Lista de EPCs recebida via Intent
        listaEpcs = getIntent().getStringArrayListExtra("listaEpcs");

        // Configura ListView
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                listaResultados);
        lvEpcs.setAdapter(adapter);

        // Botão Checar
        btnChecar.setOnClickListener(v -> {
            if (listaEpcs != null && !listaEpcs.isEmpty()) {
                listaResultados.clear(); // Limpa resultados anteriores
                adapter.notifyDataSetChanged();
                new ConsultaTagsTask().execute();
            }
        });

        // Botão Logs
        Button btnLog = findViewById(R.id.btnLog);
        btnLog.setOnClickListener(v -> {
            Intent intent = new Intent(ObjetcActivity.this, LogsActivity.class);
            intent.putStringArrayListExtra("logs", logsRequests);
            startActivity(intent);
        });

        // Mostra dados do setup no TextView
        String senhaMascarada = senha.replaceAll(".", "*");
        String dados = "Email: " + email
                + "\nSenha: " + senhaMascarada
                + "\nSerial: " + serial
                + "\nBase URL: " + baseUrl;
        //txtDadosRetirada.setText(dados);

        // Ao clicar no botão Retirada, abre OperadoresActivity
        btnRetirada.setOnClickListener(v -> {
            Intent intent = new Intent(ObjetcActivity.this, OperadoresActivity.class);
            intent.putExtra("acao", "entregar"); // para entregar
            startActivity(intent);
        });
    } // Fim do onCreate

    private class ConsultaTagsTask extends AsyncTask<Void, String, Void> {

        // Listas para armazenar resultados
        private final List<String> listaTagsSucesso = Collections.synchronizedList(new ArrayList<>());
        private final List<String> listaTagsErro404 = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected Void doInBackground(Void... voids) {
            ExecutorService executor = Executors.newFixedThreadPool(5); // Threads paralelas

            for (String tag : listaEpcs) {
                executor.submit(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("serial", serial);
                        body.put("tagRFID", tag);

                        URL url = new URL(baseUrl + "/api/v1/find_tag");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setDoOutput(true);

                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                        }

                        int responseCode = conn.getResponseCode();

                        if (responseCode == 200) {
                            // Lê resposta completa
                            StringBuilder sb = new StringBuilder();
                            try (Scanner s = new Scanner(conn.getInputStream()).useDelimiter("\\A")) {
                                if (s.hasNext()) sb.append(s.next());
                            }

                            JSONObject respJson = new JSONObject(sb.toString());
                            JSONObject data = respJson.optJSONObject("data");
                            String objeto = (data != null) ? data.optString("object", "Desconhecido") : "Sem dados";
                            String statusApi = respJson.optString("status", "UNKNOWN");

                            // Monta resultado para exibir
                            String resultado = objeto + " - " + tag + " [Status: " + statusApi + "]";

                            // Armazena e publica para interface
                            listaTagsSucesso.add(tag);
                            publishProgress(resultado);

                        } else if (responseCode == 404) {
                            // Salva erro 404, mas não exibe na tela
                            listaTagsErro404.add(tag);
                        }

                    } catch (Exception ignored) {}
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            listaResultados.add(values[0]);     // Exibe na tela
            adapter.notifyDataSetChanged();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            btnRetirada.setVisibility(View.VISIBLE);

            // Logs opcionais para depuração
            Log.d("CONSULTA", "Sucesso: " + listaTagsSucesso);
            Log.d("CONSULTA", "Erro 404: " + listaTagsErro404);
        }
    }
}
