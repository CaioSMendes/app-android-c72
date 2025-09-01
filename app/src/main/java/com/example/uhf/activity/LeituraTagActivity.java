package com.example.uhf.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LeituraTagActivity extends AppCompatActivity {

    private static final String TAG_LOG = "LeituraTag";

    // Views
    private Button btnInventory, btnClearTags, btnAcaoDefault;
    private TextView tvTagCount;
    private ListView lvTags;
    private CheckBox cbFilter;
    private Button rbSingle, rbLoop;

    // RFID
    private RFIDWithUHFUART mReader;
    private boolean isReading = false;
    private boolean modoSingle = false; // Loop padrão

    // Listas de tags
    private Set<String> tagsLidas = new HashSet<>();
    private List<TagItem> listaTagItems = new ArrayList<>();
    private List<String> tagsEncontradas = new ArrayList<>();
    private List<String> tagsNaoEncontradas = new ArrayList<>();
    private List<String> objetosEncontrados = new ArrayList<>();
    private List<String> idsInternosEncontrados = new ArrayList<>();

    // Adapters
    private TagItemAdapter tagItemAdapter;

    // Handlers e Threads
    private Handler handler = new Handler();
    private Runnable leituraRunnable;
    private ToneGenerator toneGen;
    private HandlerThread readerThread;
    private Handler readerHandler;

    // Ação recebida da Intent
    private String acao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leitura_tag);

        // Recupera a ação da Intent
        acao = getIntent().getStringExtra("acao");
        Log.d(TAG_LOG, "Ação recebida: " + acao);

        inicializarViews();
        configurarAdapters();
        configurarBotoes();
        inicializarLeitorRFID();
    }

    /** Inicializa todas as views da Activity */
    private void inicializarViews() {
        btnInventory = findViewById(R.id.btnStartInventory);
        btnClearTags = findViewById(R.id.btnClearTags);
        btnAcaoDefault = findViewById(R.id.btnAcaoDefault);
        tvTagCount = findViewById(R.id.tvTagCount);
        lvTags = findViewById(R.id.lvTags);
        cbFilter = findViewById(R.id.cbFilter);
        rbSingle = findViewById(R.id.rbSingle);
        rbLoop = findViewById(R.id.rbLoop);

        lvTags.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        lvTags.setItemsCanFocus(false);

        // Botão de ação começa oculto
        btnAcaoDefault.setVisibility(View.GONE);

        // Inicializa ToneGenerator
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // Configura modo de leitura
        rbSingle.setOnClickListener(v -> modoSingle = true);
        rbLoop.setOnClickListener(v -> modoSingle = false);
    }

    /** Configura o adapter e seleção da ListView */
    private void configurarAdapters() {
        tagItemAdapter = new TagItemAdapter(this, listaTagItems);
        lvTags.setAdapter(tagItemAdapter);

        // Listener para atualizar botão quando seleção muda
        tagItemAdapter.setOnItemSelectedListener(this::atualizarVisibilidadeBotaoAcao);
    }

    /** Configura os botões da tela */
    private void configurarBotoes() {
        // Iniciar / Parar leitura
        btnInventory.setOnClickListener(v -> {
            if (!isReading) iniciarLeitura();
            else pararLeitura();
        });

        // Limpar tags lidas
        btnClearTags.setOnClickListener(v -> limparLeitura());

        // Botão de ação confirma seleção
        btnAcaoDefault.setOnClickListener(v -> confirmarSelecao());
    }

    /** Inicializa o leitor RFID em thread separada */
    private void inicializarLeitorRFID() {
        readerThread = new HandlerThread("RFIDReaderThread");
        readerThread.start();
        readerHandler = new Handler(readerThread.getLooper());
        readerHandler.post(this::inicializarLeitor);
    }

    /** Inicializa o leitor RFID */
    private void inicializarLeitor() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
            if (mReader.init(this)) {
                runOnUiThread(() -> Toast.makeText(this, "Leitor RFID inicializado.", Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Erro ao inicializar leitor.", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
            try { mReader.stopInventory(); } catch (Exception ignored) {}
        } catch (Exception e) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Erro crítico ao inicializar leitor.", Toast.LENGTH_LONG).show();
                finish();
            });
        }
    }

    /** Atualiza visibilidade do botão de ação conforme seleção */
    private void atualizarVisibilidadeBotaoAcao() {
        boolean algumSelecionado = false;
        for (TagItem item : listaTagItems) {
            if (item.isSelecionado()) {
                algumSelecionado = true;
                break;
            }
        }

        if (algumSelecionado) {
            // Define o texto do botão de acordo com a ação
            String textoBotao;
            int iconeRes;

            switch (acao) {
                case "retirada":
                    textoBotao = "Confirmar Retirada";
                    iconeRes = R.drawable.ic_retirada;
                    break;
                case "entrega":
                    textoBotao = "Confirmar Entrega";
                    iconeRes = R.drawable.ic_entrega;
                    break;
                case "cadastro":
                    textoBotao = "Confirmar Cadastro";
                    iconeRes = R.drawable.ic_cadastro;
                    break;
                default:
                    textoBotao = "Confirmar";
                    iconeRes = 0;
            }

            btnAcaoDefault.setText(textoBotao);

            // Adiciona o ícone à esquerda do texto
            if (iconeRes != 0) {
                btnAcaoDefault.setCompoundDrawablesWithIntrinsicBounds(iconeRes, 0, 0, 0);
                btnAcaoDefault.setCompoundDrawablePadding(16); // espaço entre ícone e texto
            }

            btnAcaoDefault.setVisibility(View.VISIBLE);
            btnInventory.setVisibility(View.VISIBLE);
            btnClearTags.setVisibility(View.VISIBLE);

        } else {
            btnAcaoDefault.setVisibility(View.GONE);
        }
    }

    /** Confirma os itens selecionados */
    private void confirmarSelecao() {
        ArrayList<String> selecionadosRFID = new ArrayList<>();
        ArrayList<String> selecionadosID = new ArrayList<>();
        ArrayList<String> selecionadosObjeto = new ArrayList<>();

        for (TagItem item : listaTagItems) {
            if (item.isSelecionado()) {
                selecionadosRFID.add(item.tagRFID);
                selecionadosID.add(item.idInterno);
                selecionadosObjeto.add(item.objeto);
            }
        }

        Log.d(TAG_LOG, "Tags RFID selecionadas: " + selecionadosRFID);
        Log.d(TAG_LOG, "IDs Internos selecionados: " + selecionadosID);
        Log.d(TAG_LOG, "Objetos selecionados: " + selecionadosObjeto);

        Toast.makeText(this, "Itens selecionados: " + selecionadosRFID.size(), Toast.LENGTH_SHORT).show();
    }

    /** Limpa todas as listas e reseta a interface */
    private void limparLeitura() {
        if (isReading) pararLeitura();

        tagsLidas.clear();
        listaTagItems.clear();
        objetosEncontrados.clear();
        idsInternosEncontrados.clear();
        tagsEncontradas.clear();
        tagsNaoEncontradas.clear();

        tagItemAdapter.notifyDataSetChanged();
        tvTagCount.setText("0");
        lvTags.setSelection(0);
        btnInventory.setText("Iniciar Leitura");
    }

    /** Inicia leitura RFID */
    private void iniciarLeitura() {
        if (mReader == null) {
            Toast.makeText(this, "Leitor ainda não inicializado!", Toast.LENGTH_SHORT).show();
            return;
        }

        isReading = true;
        btnInventory.setText("Parar Leitura");
        tagsLidas.clear();
        listaTagItems.clear();
        tagItemAdapter.notifyDataSetChanged();
        tvTagCount.setText("0");

        readerHandler.post(() -> {
            try {
                if (!mReader.startInventoryTag()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Falha ao iniciar inventário.", Toast.LENGTH_SHORT).show();
                        isReading = false;
                        btnInventory.setText("Iniciar Leitura");
                    });
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG_LOG, "Erro ao iniciar inventário: " + e.getMessage());
            }
        });

        leituraRunnable = new Runnable() {
            @Override
            public void run() {
                if (isReading && mReader != null) {
                    UHFTAGInfo tagInfo = mReader.readTagFromBuffer();
                    if (tagInfo != null) {
                        String epc = tagInfo.getEPC();
                        if (!tagsLidas.contains(epc)) {
                            tagsLidas.add(epc);
                            buscarTagApi(epc);
                            toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                            runOnUiThread(() -> tvTagCount.setText("Tags lidas: " + listaTagItems.size()));
                        }
                    }
                    handler.postDelayed(this, 100);
                }
            }
        };
        handler.post(leituraRunnable);
    }

    /** Para leitura RFID */
    private void pararLeitura() {
        isReading = false;
        btnInventory.setText("Iniciar Leitura");
        handler.removeCallbacks(leituraRunnable);
        readerHandler.post(() -> {
            try { if (mReader != null) mReader.stopInventory(); }
            catch (Exception e) { Log.e(TAG_LOG, "Erro ao parar inventário: " + e.getMessage()); }
        });
    }

    /** Busca tag na API */
    private void buscarTagApi(String tagRFID) {
        Log.d(TAG_LOG, "Iniciando busca para tag: " + tagRFID + " | Ação: " + acao);

        SharedPreferences prefs = getSharedPreferences("SetupPrefs", MODE_PRIVATE);
        String baseUrl = prefs.getString("baseUrl", "http://smartlockerbrasiliarfid.com.br");
        String serial = prefs.getString("serial", "");
        String urlString = baseUrl + "/api/v1/find_tag";
        String jsonBody = "{ \"serial\": \"" + serial + "\", \"tagRFID\": \"" + tagRFID + "\" }";

        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                conn.disconnect();

                // TAG ENCONTRADA NA API (200)
                if (responseCode == 200) {
                    JSONObject json = new JSONObject(response.toString());
                    if ("SUCCESS".equals(json.getString("status"))) {
                        JSONObject data = json.getJSONObject("data");
                        String objeto = data.getString("object");
                        String idInterno = data.getString("idInterno");

                        // Apenas para exibição na UI: primeiros 6 dígitos
                        String tagExibir = tagRFID.length() >= 6 ? tagRFID.substring(0, 6) : tagRFID;

                        // Se não estiver no modo Cadastro, exibe normalmente
                        if (!"Cadastro".equalsIgnoreCase(acao)) {
                            Log.d(TAG_LOG, "Tag encontrada exibida: " + tagExibir);

                            runOnUiThread(() -> {
                                // Salva a tag completa no objeto, exibe 6 primeiros dígitos no Adapter
                                TagItem item = new TagItem(tagRFID, objeto, idInterno);
                                listaTagItems.add(item);
                                tagItemAdapter.notifyDataSetChanged();
                                tvTagCount.setText(String.valueOf(listaTagItems.size()));
                                Log.d(TAG_LOG, "Lista atual: " + listaTagItems.size() + " itens.");
                            });
                        } else {
                            Log.d(TAG_LOG, "Tag encontrada ignorada no Cadastro: " + tagExibir);
                        }

                        synchronized (tagsEncontradas) { tagsEncontradas.add(tagRFID); }
                    }
                }
                // TAG NÃO ENCONTRADA (404)
                else if (responseCode == 404) {
                    synchronized (tagsNaoEncontradas) { tagsNaoEncontradas.add(tagRFID); }

                    // Exibir apenas se estiver em Cadastro
                    if ("Cadastro".equalsIgnoreCase(acao)) {
                        Log.d(TAG_LOG, "Tag NÃO encontrada exibida no Cadastro: " + tagRFID);

                        // Apenas para exibição na UI: primeiros 6 dígitos
                        String tagExibir = tagRFID.length() >= 6 ? tagRFID.substring(0, 6) : tagRFID;

                        runOnUiThread(() -> {
                            // Salva a tag completa, exibe 6 dígitos
                            TagItem item = new TagItem(tagRFID, "Não encontrado", "");
                            listaTagItems.add(item);
                            tagItemAdapter.notifyDataSetChanged();
                            tvTagCount.setText(String.valueOf(listaTagItems.size()));
                            Log.d(TAG_LOG, "Lista atual (Cadastro): " + listaTagItems.size() + " itens.");
                        });
                    } else {
                        Log.d(TAG_LOG, "Tag NÃO encontrada ignorada (acao=" + acao + "): " + tagRFID);
                    }
                }
                // OUTROS CÓDIGOS DE ERRO (500, 400 etc.)
                else {
                    Log.d(TAG_LOG, "Erro HTTP " + responseCode + " para tag: " + tagRFID);
                    synchronized (tagsNaoEncontradas) { tagsNaoEncontradas.add(tagRFID); }
                }

            } catch (Exception e) {
                Log.e(TAG_LOG, "Erro ao buscar tag " + tagRFID, e);
                synchronized (tagsNaoEncontradas) { tagsNaoEncontradas.add(tagRFID); }
            }
        }).start();
    }


    /** Captura evento de trigger físico */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int triggerKeyCode = 293;
        if (event.getKeyCode() == triggerKeyCode) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (modoSingle && !isReading) {
                    iniciarLeitura();
                    handler.postDelayed(this::pararLeitura, 200);
                } else if (!modoSingle && !isReading) {
                    iniciarLeitura();
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP && !modoSingle && isReading) {
                pararLeitura();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pararLeitura();
        if (mReader != null) try { mReader.free(); } catch (Exception e) { Log.e(TAG_LOG, "Erro ao liberar leitor", e); }
        if (readerThread != null) readerThread.quitSafely();
    }

    /** Adapter customizado para ListView de tags */
    private static class TagItemAdapter extends BaseAdapter {
        private final List<TagItem> items;
        private final LayoutInflater inflater;

        public interface OnItemSelectedListener { void onItemSelectionChanged(); }
        private OnItemSelectedListener listener;
        public void setOnItemSelectedListener(OnItemSelectedListener listener) { this.listener = listener; }

        public TagItemAdapter(Context context, List<TagItem> items) {
            this.items = items;
            this.inflater = LayoutInflater.from(context);
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.imagem_rfid_tag, parent, false);
                holder = new ViewHolder();
                holder.tvTag = convertView.findViewById(R.id.tvTagText);
                holder.tvObjeto = convertView.findViewById(R.id.tvObject);
                holder.tvIdInterno = convertView.findViewById(R.id.tvIdInterno);
                holder.imgTag = convertView.findViewById(R.id.imgTag);
                convertView.setTag(holder);
            } else holder = (ViewHolder) convertView.getTag();

            TagItem item = items.get(position);

            // Mostra apenas os 6 primeiros dígitos na tela
            String tagExibir = item.tagRFID.length() >= 6 ? item.tagRFID.substring(0, 6) : item.tagRFID;
            holder.tvTag.setText(tagExibir);

            holder.tvObjeto.setText(item.objeto);
            holder.tvIdInterno.setText(item.idInterno);
            holder.imgTag.setImageResource(R.drawable.tagrfid);

            convertView.setBackgroundColor(item.isSelecionado() ? Color.parseColor("#CCCCCC") : Color.TRANSPARENT);

            convertView.setOnClickListener(v -> {
                item.setSelecionado(!item.isSelecionado());
                notifyDataSetChanged();
                if (listener != null) listener.onItemSelectionChanged();
            });

            return convertView;
        }


        static class ViewHolder {
            TextView tvTag, tvObjeto, tvIdInterno;
            ImageView imgTag;
        }
    }

    /** Classe de modelo para cada tag */
    private static class TagItem {
        private final String tagRFID;
        private final String objeto;
        private final String idInterno;
        private boolean selecionado = false;

        public TagItem(String tagRFID, String objeto, String idInterno) {
            this.tagRFID = tagRFID;
            this.objeto = objeto;
            this.idInterno = idInterno;
        }

        public String getIdTag() { return tagRFID; }
        public boolean isSelecionado() { return selecionado; }
        public void setSelecionado(boolean selecionado) { this.selecionado = selecionado; }
    }
}
