package com.example.uhf.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.example.uhf.AppData;  // import da sua classe global


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
    private String tagRFID; // pode receber via Intent, ajustar conforme

    private int giverId;    // exemplo, de quem entrega (ajuste conforme no app)
    private int receiverId; // exemplo, de quem recebe (ajuste conforme no app)

    private ArrayList<String> listaTags; // <-- aqui

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_funcionarios);
        // Recebe o ID do operador
        giverId = getIntent().getIntExtra("operadorId", -1);

        if (giverId == -1) {
            Toast.makeText(this, "Erro: ID do operador não recebido!", Toast.LENGTH_LONG).show();
        }

        // Recebe a lista de tags
        listaTags = getIntent().getStringArrayListExtra("listaTags");
        if (listaTags == null) listaTags = new ArrayList<>();

        lvFuncionarios = findViewById(R.id.lvFuncionarios);
        txtFuncionariosSelecionados = findViewById(R.id.txtFuncionariosSelecionados);
        btnConfirmarEntrega = findViewById(R.id.btnConfirmarEntrega);
        btnConfirmarDevolucao = findViewById(R.id.btnConfirmarDevolucao);

        SharedPreferences prefs = getSharedPreferences("SetupPrefs", MODE_PRIVATE);
        baseUrl = prefs.getString("baseUrl", "");
        serial = prefs.getString("serial", "");

        buscarFuncionarios();

        lvFuncionarios.setOnItemClickListener((parent, view, position, id) -> {
            int funcionarioId = idsFuncionarios.get(position);
            if (selecionados.contains(funcionarioId)) {
                selecionados.remove((Integer) funcionarioId);
            } else {
                selecionados.add(funcionarioId);
            }
            txtFuncionariosSelecionados.setText("IDs selecionados: " + selecionados.toString());
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
            if (selecionados.contains(funcionarioId)) {
                convertView.setBackgroundColor(0xFFE0F7FA); // azul claro se selecionado
            } else {
                convertView.setBackgroundColor(0x00000000); // transparente se não
            }

            img.setImageResource(R.drawable.ic_soldado); // ícone padrão, pode trocar por dinâmico
            return convertView;
        }
    }
    private void buscarFuncionarios() {
        new Thread(() -> {
            try {
                String urlString = baseUrl + "/api/v1/check_operator_access?serial=" + serial + "&only_operators=false";
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
        FuncionarioAdapter adapter = new FuncionarioAdapter(listaFuncionarios, idsFuncionarios);
        lvFuncionarios.setAdapter(adapter);
        lvFuncionarios.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        lvFuncionarios.setOnItemClickListener((parent, view, position, id) -> {
            int funcionarioId = idsFuncionarios.get(position);
            if (selecionados.contains(funcionarioId)) {
                selecionados.remove((Integer) funcionarioId);
            } else {
                selecionados.add(funcionarioId);
            }
            txtFuncionariosSelecionados.setText("IDs selecionados: " + selecionados.toString());
            adapter.notifyDataSetChanged(); // atualiza cor de fundo
        });
    }

    private void enviarRequisicoes(String actionType) {
        // Puxa a lista de tags global
        List<String> listaTags = AppData.listaTagsSucesso;

        if (listaTags == null || listaTags.isEmpty()) {
            Toast.makeText(this, "Lista de tags vazia ou nula!", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this,
                "Enviando " + listaTags.size() + " tags para " + selecionados.size() + " funcionários.",
                Toast.LENGTH_SHORT).show();

        int delay = 0; // tempo inicial
        for (String tag : listaTags) { // agora percorre todas as tags
            for (int receiverId : selecionados) { // para cada funcionário
                final int id = receiverId;
                final String tagAtual = tag;

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Toast.makeText(this,
                            "Tag: " + tagAtual +
                                    " | Serial: " + serial +
                                    " | ID do Entregador: " + giverId +
                                    " | ID do Recebedor: " + id +
                                    " | Ação: " + actionType,
                            Toast.LENGTH_SHORT).show();
                }, delay);

                delay += 2500; // espera antes do próximo envio
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
                    // Inverte giver e receiver na devolução
                    jsonBody.put("giver_id", receiverId);
                    jsonBody.put("receiver_id", giverId);
                } else {
                    // Mantém normal para entregar
                    jsonBody.put("giver_id", giverId);
                    jsonBody.put("receiver_id", receiverId);
                }

                jsonBody.put("locker_serial", lockerSerial);
                jsonBody.put("action_type", actionType);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;

                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                br.close();

                String acaoMensagem = actionType.equals("entregar") ? "entregue" : "devolvida";

                runOnUiThread(() -> Toast.makeText(FuncionariosActivity.this,
                        "Tag " + acaoMensagem + " com sucesso! Código: " + responseCode,
                        Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                //runOnUiThread(() -> Toast.makeText(FuncionariosActivity.this,
                //"Erro: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
