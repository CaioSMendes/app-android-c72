package com.example.uhf.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FuncionariosActivity extends AppCompatActivity {

    private ListView lvFuncionarios;
    private TextView txtFuncionariosSelecionados;
    private ArrayList<Integer> idsFuncionarios = new ArrayList<>();
    private ArrayList<String> nomesFuncionarios = new ArrayList<>();
    private ArrayList<Integer> selecionados = new ArrayList<>(); // IDs selecionados

    private Button btnConfirmarEntrega;
    private Button btnConfirmarDevolucao;
    private String baseUrl;
    private String serial;
    private int giverId; // ID do operador selecionado
    private ArrayList<String> listaTags; // lista de tags recebida
    private String acao;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_funcionarios);

        lvFuncionarios = findViewById(R.id.lvFuncionarios);
        txtFuncionariosSelecionados = findViewById(R.id.txtFuncionariosSelecionados);
        btnConfirmarEntrega = findViewById(R.id.btnConfirmarEntrega);
        btnConfirmarDevolucao = findViewById(R.id.btnConfirmarDevolucao);

        // Recebe ID do operador da Intent
        giverId = getIntent().getIntExtra("operadorId", -1);
        if (giverId == -1) {
            Toast.makeText(this, "Erro: ID do operador não recebido!", Toast.LENGTH_LONG).show();
        }

        // Recebe lista de tags da Intent
        listaTags = getIntent().getStringArrayListExtra("listaTags");
        if (listaTags != null && !listaTags.isEmpty()) {
            for (String tag : listaTags) {
                Log.d("FuncionariosActivity", "Tag recebida: " + tag);
            }
        } else {
            Log.d("FuncionariosActivity", "Nenhuma tag recebida");
            listaTags = new ArrayList<>();
        }

        // Recupera configurações
        SharedPreferences prefs = getSharedPreferences("SetupPrefs", MODE_PRIVATE);
        baseUrl = prefs.getString("baseUrl", "");
        serial = prefs.getString("serial", "");

        // Carrega lista de funcionários
        buscarFuncionarios();

        // Clique em item da lista
        lvFuncionarios.setOnItemClickListener((parent, view, position, id) -> {
            int funcionarioId = idsFuncionarios.get(position);
            if (selecionados.contains(funcionarioId)) {
                selecionados.remove((Integer) funcionarioId);
            } else {
                selecionados.add(funcionarioId);
            }
            txtFuncionariosSelecionados.setText("IDs selecionados: " + selecionados.toString());
            ((ArrayAdapter) lvFuncionarios.getAdapter()).notifyDataSetChanged();
        });

        btnConfirmarEntrega.setOnClickListener(v -> {
            if (selecionados.isEmpty()) {
                Toast.makeText(this, "Selecione pelo menos um funcionário", Toast.LENGTH_SHORT).show();
                return;
            }
            enviarRequisicoes("entregar");
        });

        btnConfirmarDevolucao.setOnClickListener(v -> {
            if (selecionados.isEmpty()) {
                Toast.makeText(this, "Selecione pelo menos um funcionário", Toast.LENGTH_SHORT).show();
                return;
            }
            enviarRequisicoes("devolver");
        });
    }

    private void buscarFuncionarios() {
        new Thread(() -> {
            try {
                String urlString = baseUrl + "/api/v1/check_operator_access?serial=" + serial + "&only_operators=false";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
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

                    ArrayList<String> listaFuncionarios = new ArrayList<>();
                    idsFuncionarios.clear();
                    nomesFuncionarios.clear();

                    for (int i = 0; i < data.length(); i++) {
                        JSONObject funcionario = data.getJSONObject(i);
                        String nomeCompleto = funcionario.getString("name") + " " + funcionario.getString("lastname");
                        String info = "ID: " + funcionario.getInt("id")
                                + "\nNome: " + nomeCompleto
                                + "\nEmail: " + funcionario.getString("email")
                                + "\nMatrícula: " + funcionario.getString("matricula");
                        listaFuncionarios.add(info);
                        idsFuncionarios.add(funcionario.getInt("id"));
                        nomesFuncionarios.add(nomeCompleto);
                    }

                    runOnUiThread(() -> atualizarListaFuncionarios(listaFuncionarios));
                } else {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Erro ao carregar funcionários: status " + responseCode, Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void atualizarListaFuncionarios(ArrayList<String> listaFuncionarios) {
        ArrayAdapter<String> adapter = new FuncionarioAdapter(listaFuncionarios, idsFuncionarios);
        lvFuncionarios.setAdapter(adapter);
    }

    private class FuncionarioAdapter extends ArrayAdapter<String> {
        private final ArrayList<String> lista;
        private final ArrayList<Integer> ids;

        public FuncionarioAdapter(ArrayList<String> lista, ArrayList<Integer> ids) {
            super(FuncionariosActivity.this, 0, lista);
            this.lista = lista;
            this.ids = ids;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_funcionario, parent, false);
            }

            ImageView img = convertView.findViewById(R.id.imgFuncionario);
            TextView txt = convertView.findViewById(R.id.txtInfoFuncionario);

            txt.setText(lista.get(position));
            int funcionarioId = ids.get(position);
            convertView.setBackgroundColor(selecionados.contains(funcionarioId) ? 0xFFE0F7FA : 0x00000000);
            img.setImageResource(R.drawable.ic_soldado);

            return convertView;
        }
    }

    private void enviarRequisicoes(String actionType) {
        if (listaTags == null || listaTags.isEmpty()) {
            Toast.makeText(this, "Lista de tags vazia!", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Enviando " + listaTags.size() + " tags para " + selecionados.size() + " funcionários.", Toast.LENGTH_SHORT).show();

        int totalEnvios = listaTags.size() * selecionados.size();
        final int[] enviosConcluidos = {0}; // contador para rastrear envios

        int delay = 0;
        for (String tag : listaTags) {
            for (int receiverId : selecionados) {
                final int id = receiverId;
                final String tagAtual = tag;

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Log.d("FuncionariosActivity", "Enviando tag: " + tagAtual + " para ID: " + id + " | Ação: " + actionType);

                    // Incrementa contador
                    enviosConcluidos[0]++;

                    // Se todos os envios concluídos, mostra popup
                    if (enviosConcluidos[0] >= totalEnvios) {
                        runOnUiThread(() -> {
                            new androidx.appcompat.app.AlertDialog.Builder(FuncionariosActivity.this)
                                    .setTitle("Sucesso")
                                    .setMessage("Todas as tags foram " + (actionType.equals("entregar") ? "entregues" : "devolvidas") + " com sucesso!")
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }
                }, delay);

                delay += 2500;
                enviarTransacao(tagAtual, giverId, receiverId, serial, actionType);
            }
        }
    }

    private void enviarTransacao(String tagRFID, int giverId, int receiverId, String lockerSerial, String actionType) {
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/api/v1/keylocker_transactions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("tagRFID", tagRFID);

                if ("devolver".equals(actionType)) {
                    jsonBody.put("giver_id", receiverId);
                    jsonBody.put("receiver_id", giverId);
                } else {
                    jsonBody.put("giver_id", giverId);
                    jsonBody.put("receiver_id", receiverId);
                }

                jsonBody.put("locker_serial", lockerSerial);
                jsonBody.put("action_type", actionType);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes("utf-8"));
                }

                int responseCode = conn.getResponseCode();
                Log.d("FuncionariosActivity", "Transação enviada | Tag: " + tagRFID + " | Código: " + responseCode);

            } catch (Exception e) {
                Log.e("FuncionariosActivity", "Erro ao enviar transação: " + e.getMessage());
            }
        }).start();
    }
}