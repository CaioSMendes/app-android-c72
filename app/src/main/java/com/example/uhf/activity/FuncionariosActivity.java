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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
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
        acao = getIntent().getStringExtra("acao");
        listaTags = getIntent().getStringArrayListExtra("listaTags");
        // Esconde/mostra os botões de acordo com a ação
        if ("entrega".equalsIgnoreCase(acao)) {
            btnConfirmarDevolucao.setVisibility(View.GONE); // esconde devolução
            btnConfirmarEntrega.setVisibility(View.VISIBLE); // mostra entrega
        } else if ("retirada".equalsIgnoreCase(acao)) {
            btnConfirmarEntrega.setVisibility(View.GONE); // esconde entrega
            btnConfirmarDevolucao.setVisibility(View.VISIBLE); // mostra devolução
        } else {
            // Se não vier nada ou vier inválido, esconde ambos (evita erro)
            btnConfirmarEntrega.setVisibility(View.GONE);
            btnConfirmarDevolucao.setVisibility(View.GONE);
            Log.w("FuncionariosActivity", "Ação inválida recebida: " + acao);
        }

        if (listaTags != null && !listaTags.isEmpty()) {
            for (String tag : listaTags) {
                Log.d("FuncionariosActivity", "Tag recebida: " + tag);
                Log.d("FuncionariosActivity", "Ação recebida: " + acao);
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
        final int[] enviosConcluidos = {0};
        final int[] sucesso = {0};
        final int[] falha = {0};
        List<String> erros = Collections.synchronizedList(new ArrayList<>());

        for (String tag : listaTags) {
            for (int receiverId : selecionados) {
                final int id = receiverId;
                final String tagAtual = tag;

                enviarTransacao(tagAtual, giverId, receiverId, serial, actionType, (result, errorMsg) -> {
                    enviosConcluidos[0]++;
                    if (result) {
                        sucesso[0]++;
                    } else {
                        falha[0]++;
                        if (errorMsg != null && !errorMsg.isEmpty()) {
                            erros.add("Tag " + tagAtual + ": " + errorMsg);
                        }
                    }

                    if (enviosConcluidos[0] >= totalEnvios) {
                        runOnUiThread(() -> {
                            StringBuilder msgFinal = new StringBuilder();
                            msgFinal.append("Operação finalizada!\n\n")
                                    .append("Sucesso: ").append(sucesso[0]).append("\n")
                                    .append("Falha: ").append(falha[0]);

                            if (!erros.isEmpty()) {
                                msgFinal.append("\n\nDetalhes dos erros:\n");
                                for (String erro : erros) {
                                    msgFinal.append("- ").append(erro).append("\n");
                                }
                            }

                            new androidx.appcompat.app.AlertDialog.Builder(FuncionariosActivity.this)
                                    .setTitle("Resultado da Operação")
                                    .setMessage(msgFinal.toString())
                                    .setPositiveButton("OK", (dialog, which) -> finish())
                                    .setCancelable(false)
                                    .show();
                        });
                    }
                });
            }
        }
    }

    public interface TransacaoCallback {
        void onResult(boolean sucesso, String mensagemErro);
    }

    private void enviarTransacao(String tagRFID, int giverId, int receiverId, String lockerSerial, String actionType,
                                 TransacaoCallback callback) {
        new Thread(() -> {
            boolean sucessoEnvio = false;
            String errorMsg = null;
            StringBuilder response = new StringBuilder(); // declarado fora do try para logar sempre

            try {
                URL url = new URL(baseUrl + "/api/v1/keylocker_transactions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("tagRFID", tagRFID);

                if ("devolver".equals(actionType)) {
                    jsonBody.put("receiver_id", giverId);
                    jsonBody.put("giver_id", receiverId);
                } else {
                    jsonBody.put("giver_id", giverId);
                    jsonBody.put("receiver_id", receiverId);
                }

                jsonBody.put("locker_serial", lockerSerial);
                jsonBody.put("action_type", actionType);

                // envia o JSON
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();

                // lê o inputStream correto mesmo em erro
                InputStream is;
                if (responseCode >= 200 && responseCode < 400) {
                    is = conn.getInputStream();
                } else {
                    is = conn.getErrorStream();
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
                String line;
                while ((line = br.readLine()) != null) response.append(line.trim());
                br.close();

                JSONObject jsonResponse = new JSONObject(response.toString());

                if (responseCode >= 200 && responseCode < 300 &&
                        "SUCCESS".equalsIgnoreCase(jsonResponse.optString("status"))) {
                    sucessoEnvio = true;
                } else {
                    errorMsg = jsonResponse.optString("message", "Erro desconhecido");
                }

                Log.d("FuncionariosActivity", "Tag " + tagRFID + " enviada para " + receiverId +
                        " | Código: " + responseCode + " | Resposta: " + response);

            } catch (Exception e) {
                errorMsg = "Erro ao enviar tag " + tagRFID + ": " + e.getMessage();
                Log.e("FuncionariosActivity", errorMsg, e);
                Log.e("FuncionariosActivity", "Resposta parcial: " + response.toString());
            } finally {
                if (callback != null) callback.onResult(sucessoEnvio, errorMsg);
            }

        }).start();
    }
}
