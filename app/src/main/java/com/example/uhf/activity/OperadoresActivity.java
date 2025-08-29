package com.example.uhf.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class OperadoresActivity extends AppCompatActivity {
    private ListView lvOperadores;
    private TextView txtOperadorSelecionado;

    private ArrayList<Integer> idsOperadores = new ArrayList<>();
    private ArrayList<String> nomesCompleto = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operadores);

        lvOperadores = findViewById(R.id.lvOperadores);
        txtOperadorSelecionado = findViewById(R.id.txtOperadorSelecionado);

        SharedPreferences prefs = getSharedPreferences("SetupPrefs", MODE_PRIVATE);
        String baseUrl = prefs.getString("baseUrl", "");
        String serial = prefs.getString("serial", "");

        buscarOperadores(baseUrl, serial);
    }

    private void buscarOperadores(String baseUrl, String serial) {
        new Thread(() -> {
            try {
                String urlString = baseUrl + "/api/v1/check_operator_access?serial=" + serial + "&only_operators=true";
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-Type", "application/json");
                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONArray data = jsonResponse.getJSONArray("data");

                    ArrayList<String> listaOperadores = new ArrayList<>();
                    idsOperadores.clear();
                    nomesCompleto.clear();

                    for (int i = 0; i < data.length(); i++) {
                        JSONObject operador = data.getJSONObject(i);
                        String nomeCompletoStr = operador.getString("name") + " " + operador.getString("lastname");
                        String info = "ID: " + operador.getInt("id") + "\nNome: " + nomeCompletoStr + "\nEmail: " + operador.getString("email") + "\nMatrícula: " + operador.getString("matricula");
                        listaOperadores.add(info);
                        idsOperadores.add(operador.getInt("id"));
                        nomesCompleto.add(nomeCompletoStr);
                    }

                    runOnUiThread(() -> atualizarLista(listaOperadores));
                } else {
                    runOnUiThread(() -> txtOperadorSelecionado.setText("Erro ao carregar operadores: status " + responseCode));
                }

            } catch (Exception e) {
                runOnUiThread(() -> txtOperadorSelecionado.setText("Erro: " + e.getMessage()));
            }
        }).start();
    }

    private void atualizarLista(ArrayList<String> listaOperadores) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, listaOperadores);
        lvOperadores.setAdapter(adapter);

        lvOperadores.setOnItemClickListener((parent, view, position, id) -> {
            int operadorId = idsOperadores.get(position);
            String nome = nomesCompleto.get(position);

            txtOperadorSelecionado.setText("Selecionado: " + nome + " (ID: " + operadorId + ")");
            ArrayList<String> listaTags = getIntent().getStringArrayListExtra("listaTags");

            // Salva o operador selecionado para usar depois
            SharedPreferences.Editor editor = getSharedPreferences("SetupPrefs", MODE_PRIVATE).edit();
            editor.putInt("operadorSelecionadoId", operadorId);
            editor.apply();

            // Abre a próxima Activity para escolher funcionários
            Intent intent = new Intent(OperadoresActivity.this, FuncionariosActivity.class);
            intent.putExtra("operadorId", operadorId);
            intent.putStringArrayListExtra("listaTags", listaTags);
            startActivity(intent);
        });
    }
}
