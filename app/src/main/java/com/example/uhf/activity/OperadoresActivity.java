package com.example.uhf.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operadores);

        inicializarViews();
        carregarDadosIniciais();
    }

    /** Inicializa os elementos da tela */
    private void inicializarViews() {
        lvOperadores = findViewById(R.id.lvOperadores);
        txtOperadorSelecionado = findViewById(R.id.txtOperadorSelecionado);
    }

    /** Recupera configurações iniciais e inicia a busca de operadores */
    private void carregarDadosIniciais() {
        SharedPreferences prefs = getSharedPreferences("SetupPrefs", MODE_PRIVATE);
        String baseUrl = prefs.getString("baseUrl", "");
        String serial = prefs.getString("serial", "");
        buscarOperadores(baseUrl, serial);
    }

    /** Busca operadores via API em thread separada */
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

    /** Lê a resposta da conexão HTTP */
    private String lerResposta(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) response.append(line);
        reader.close();
        return response.toString();
    }

    /** Processa os dados retornados pela API e popula listas */
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

    /** Atualiza a ListView com os operadores */
    private void atualizarLista(ArrayList<String> listaOperadores) {
        OperadorAdapter adapter = new OperadorAdapter(this, listaOperadores);
        lvOperadores.setAdapter(adapter);

        lvOperadores.setOnItemClickListener((parent, view, position, id) -> {
            int operadorId = idsOperadores.get(position);
            String nome = nomesCompleto.get(position);

            txtOperadorSelecionado.setText("Selecionado: " + nome + " (ID: " + operadorId + ")");
            salvarOperadorSelecionado(operadorId);

            // Passa lista de tags recebida via Intent
            ArrayList<String> listaTags = getIntent().getStringArrayListExtra("listaTags");

            Intent intent = new Intent(OperadoresActivity.this, FuncionariosActivity.class);
            intent.putExtra("operadorId", operadorId);
            intent.putStringArrayListExtra("listaTags", listaTags);
            startActivity(intent);
        });
    }

    /** Salva operador selecionado em SharedPreferences */
    private void salvarOperadorSelecionado(int operadorId) {
        SharedPreferences.Editor editor = getSharedPreferences("SetupPrefs", MODE_PRIVATE).edit();
        editor.putInt("operadorSelecionadoId", operadorId);
        editor.apply();
    }

    /** Exibe erro na interface */
    private void mostrarErro(String mensagem) {
        runOnUiThread(() -> txtOperadorSelecionado.setText(mensagem));
    }

    /** Adapter customizado para exibir operador com avatar */
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
            imgAvatar.setImageResource(R.drawable.ic_soldado); // Avatar fixo, pode ser dinâmico

            return convertView;
        }
    }
}