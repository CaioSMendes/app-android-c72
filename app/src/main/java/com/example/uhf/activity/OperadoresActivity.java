package com.example.uhf.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

    private final ArrayList<Integer> idsOperadores = new ArrayList<>();
    private final ArrayList<String> nomesCompleto = new ArrayList<>();

    private ArrayList<String> tagsRecebidas; // <- variável de instância
    private String acao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operadores);

        inicializarViews();

        // Recebe dados da Intent
        tagsRecebidas = getIntent().getStringArrayListExtra("tagsSelecionadas"); // mesma chave
        acao = getIntent().getStringExtra("acao"); // agora usa a variável da classe

        // Log para depuração
        if (tagsRecebidas != null) {
            for (String tag : tagsRecebidas) {
                Log.d("OperadoresActivity", "Tag recebida no onCreate: " + tag);
            }
        } else {
            Log.d("OperadoresActivity", "Nenhuma tag recebida no onCreate");
            tagsRecebidas = new ArrayList<>(); // garante lista não nula
        }
        Log.d("OperadoresActivity", "Ação recebida no onCreate: " + acao);

        carregarDadosIniciais();
    }

    private void inicializarViews() {
        lvOperadores = findViewById(R.id.lvOperadores);
        txtOperadorSelecionado = findViewById(R.id.txtOperadorSelecionado);
    }

    private void carregarDadosIniciais() {
        SharedPreferences prefs = getSharedPreferences("SetupPrefs", MODE_PRIVATE);
        String baseUrl = prefs.getString("baseUrl", "");
        String serial = prefs.getString("serial", "");
        buscarOperadores(baseUrl, serial);
    }

    private void buscarOperadores(String baseUrl, String serial) {
        new Thread(() -> {
            try {
                String urlString = baseUrl + "/api/v1/check_operator_access?serial=" + serial + "&only_operators=true";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-Type", "application/json");

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    processarResposta(lerResposta(conn));
                } else {
                    mostrarErro("Erro ao carregar operadores: status " + responseCode);
                }
            } catch (Exception e) {
                mostrarErro("Erro: " + e.getMessage());
            }
        }).start();
    }

    private String lerResposta(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) response.append(line);
        reader.close();
        return response.toString();
    }

    private void processarResposta(String respostaJson) {
        try {
            JSONObject jsonResponse = new JSONObject(respostaJson);
            JSONArray data = jsonResponse.getJSONArray("data");

            ArrayList<String> listaOperadores = new ArrayList<>();
            idsOperadores.clear();
            nomesCompleto.clear();

            for (int i = 0; i < data.length(); i++) {
                JSONObject operador = data.getJSONObject(i);
                int id = operador.getInt("id");
                String nomeCompletoStr = operador.getString("name") + " " + operador.getString("lastname");

                String info = "ID: " + id +
                        "\nNome: " + nomeCompletoStr +
                        "\nEmail: " + operador.getString("email") +
                        "\nMatrícula: " + operador.getString("matricula");

                listaOperadores.add(info);
                idsOperadores.add(id);
                nomesCompleto.add(nomeCompletoStr);
            }

            runOnUiThread(() -> atualizarLista(listaOperadores));

        } catch (Exception e) {
            mostrarErro("Erro ao processar resposta: " + e.getMessage());
        }
    }

    private void atualizarLista(ArrayList<String> listaOperadores) {
        OperadorAdapter adapter = new OperadorAdapter(this, listaOperadores);
        lvOperadores.setAdapter(adapter);

        lvOperadores.setOnItemClickListener((parent, view, position, id) -> {
            int operadorId = idsOperadores.get(position);
            String nome = nomesCompleto.get(position);

            txtOperadorSelecionado.setText("Selecionado: " + nome + " (ID: " + operadorId + ")");
            salvarOperadorSelecionado(operadorId);

            // Log do envio
            Log.d("OperadoresActivity", "Enviando para FuncionariosActivity operadorId: " + operadorId);
            Log.d("OperadoresActivity", "Enviando listaTags: " + tagsRecebidas);

            // Cria Intent e envia para FuncionariosActivity
            Intent intent = new Intent(OperadoresActivity.this, FuncionariosActivity.class);
            intent.putExtra("operadorId", operadorId);
            intent.putExtra("acao", acao);
            intent.putStringArrayListExtra("listaTags", tagsRecebidas); // passa a mesma lista que recebeu
            startActivity(intent);

            Log.d("OperadoresActivity", "Enviando operadorId: " + operadorId);
            Log.d("OperadoresActivity", "Enviando listaTags para FuncionariosActivity: " + tagsRecebidas);
            Log.d("OperadoresActivity", "Enviando ação para FuncionariosActivity: " + acao);
        });
    }

    private void salvarOperadorSelecionado(int operadorId) {
        SharedPreferences.Editor editor = getSharedPreferences("SetupPrefs", MODE_PRIVATE).edit();
        editor.putInt("operadorSelecionadoId", operadorId);
        editor.apply();
    }

    private void mostrarErro(String mensagem) {
        runOnUiThread(() -> txtOperadorSelecionado.setText(mensagem));
    }

    private static class OperadorAdapter extends ArrayAdapter<String> {

        private final Context context;
        private final ArrayList<String> listaOperadores;

        public OperadorAdapter(Context context, ArrayList<String> listaOperadores) {
            super(context, 0, listaOperadores);
            this.context = context;
            this.listaOperadores = listaOperadores;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context)
                        .inflate(R.layout.item_operador, parent, false);
            }

            ImageView imgAvatar = convertView.findViewById(R.id.imgAvatar);
            TextView txtInfo = convertView.findViewById(R.id.txtInfo);

            txtInfo.setText(listaOperadores.get(position));
            imgAvatar.setImageResource(R.drawable.ic_soldado);

            return convertView;
        }
    }
}