package com.example.uhf.activity;

import android.content.Context;
import android.content.SharedPreferences;
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

    private Button btnInventory, btnClearTags;
    private TextView tvTagCount;
    private ListView lvTags;
    private CheckBox cbFilter;
    private Button rbSingle, rbLoop;

    private RFIDWithUHFUART mReader;
    private boolean isReading = false;

    private List<String> listaTags = new ArrayList<>();
    private Set<String> tagsLidas = new HashSet<>();
    private TagAdapter adapter;

    private List<String> tagsEncontradas = new ArrayList<>();
    private List<String> tagsNaoEncontradas = new ArrayList<>();
    private List<String> objetosEncontrados = new ArrayList<>();
    private List<String> idsInternosEncontrados = new ArrayList<>();
    private List<TagItem> listaTagItems = new ArrayList<>();
    private TagItemAdapter tagItemAdapter;

    private Handler handler = new Handler();
    private Runnable leituraRunnable;
    private ToneGenerator toneGen;
    private HandlerThread readerThread;
    private Handler readerHandler;

    private String acao; // variável de instância
    private boolean modoSingle = false; // Loop como padrão

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leitura_tag);

        acao = getIntent().getStringExtra("acao");

        btnInventory = findViewById(R.id.btnStartInventory);
        btnClearTags = findViewById(R.id.btnClearTags);
        tvTagCount = findViewById(R.id.tvTagCount);
        lvTags = findViewById(R.id.lvTags);
        cbFilter = findViewById(R.id.cbFilter);
        rbSingle = findViewById(R.id.rbSingle);
        rbLoop = findViewById(R.id.rbLoop);

        rbSingle.setOnClickListener(v -> modoSingle = true);
        rbLoop.setOnClickListener(v -> modoSingle = false);

        adapter = new TagAdapter(this, listaTags);
        tagItemAdapter = new TagItemAdapter(this, listaTagItems);
        lvTags.setAdapter(tagItemAdapter);


        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        readerThread = new HandlerThread("RFIDReaderThread");
        readerThread.start();
        readerHandler = new Handler(readerThread.getLooper());
        readerHandler.post(this::inicializarLeitor);

        btnInventory.setOnClickListener(v -> {
            if (!isReading) {
                iniciarLeitura();
            } else {
                pararLeitura();
            }
        });

        btnClearTags.setOnClickListener(v -> {
            // Para a leitura caso esteja em andamento
            if (isReading) {
                pararLeitura();
            }

            // Limpa todas as listas de dados
            tagsLidas.clear();
            listaTags.clear();
            listaTagItems.clear();
            objetosEncontrados.clear();
            idsInternosEncontrados.clear();
            tagsEncontradas.clear();
            tagsNaoEncontradas.clear();

            // Atualiza o Adapter para limpar a ListView
            tagItemAdapter.notifyDataSetChanged();

            // Zera contador na tela
            tvTagCount.setText("0");

            // Move scroll da ListView para o topo
            lvTags.setSelection(0);

            // Reset botão de iniciar leitura
            btnInventory.setText("Iniciar Leitura");
        });
    }

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

    private static class TagItemAdapter extends BaseAdapter {
        private List<TagItem> items;
        private LayoutInflater inflater;

        public TagItemAdapter(Context context, List<TagItem> items) {
            this.items = items;
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() { return items.size(); }

        @Override
        public Object getItem(int position) { return items.get(position); }

        @Override
        public long getItemId(int position) { return position; }

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
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            TagItem item = items.get(position);
            holder.tvTag.setText(item.tagRFID);
            holder.tvObjeto.setText(item.objeto);
            holder.tvIdInterno.setText(item.idInterno);
            holder.imgTag.setImageResource(R.drawable.tagrfid);

            return convertView;
        }

        private static class ViewHolder {
            TextView tvTag, tvObjeto, tvIdInterno;
            ImageView imgTag;
        }
    }

    private void iniciarLeitura() {
        if (mReader == null) {
            Toast.makeText(this, "Leitor ainda não inicializado!", Toast.LENGTH_SHORT).show();
            return;
        }

        isReading = true;
        btnInventory.setText("Parar Leitura");
        tagsLidas.clear();
        listaTags.clear();
        listaTagItems.clear(); // limpa os itens da API
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
                            // Somente chamada da API
                            buscarTagApi(epc);
                            toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                        }
                    }
                    handler.postDelayed(this, 100);
                }
            }
        };
        handler.post(leituraRunnable);
    }

    private void pararLeitura() {
        isReading = false;
        btnInventory.setText("Iniciar Leitura");
        handler.removeCallbacks(leituraRunnable);
        readerHandler.post(() -> {
            try {
                if (mReader != null) mReader.stopInventory();
            } catch (Exception e) {
                Log.e(TAG_LOG, "Erro ao parar inventário: " + e.getMessage());
            }
        });
    }

    private void buscarTagApi(String tagRFID) {
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

                if (responseCode == 200) {
                    JSONObject json = new JSONObject(response.toString());
                    if ("SUCCESS".equals(json.getString("status"))) {
                        JSONObject data = json.getJSONObject("data");
                        String objeto = data.getString("object");
                        String idInterno = data.getString("idInterno");

                        // Tratar tag: pegar os primeiros 6 caracteres
                        String tagCurta = tagRFID.length() >= 6 ? tagRFID.substring(0, 6) : tagRFID;
                        String tagExibir;
                        if (tagCurta.matches("[0-9]+")) {
                            tagExibir = tagCurta;
                        } else {
                            try {
                                long decimal = Long.parseLong(tagCurta, 16);
                                tagExibir = String.format("%06d", decimal % 1000000);
                            } catch (NumberFormatException e) {
                                tagExibir = tagCurta;
                            }
                        }

                        // Atualiza a lista na UI
                        final String finalTagExibir = tagExibir;
                        final String finalObjeto = objeto;
                        final String finalIdInterno = idInterno;

                        runOnUiThread(() -> {
                            TagItem item = new TagItem(finalTagExibir, finalObjeto, finalIdInterno);
                            listaTagItems.add(item);
                            tagItemAdapter.notifyDataSetChanged();
                            tvTagCount.setText(String.valueOf(listaTagItems.size()));
                        });

                        synchronized (tagsEncontradas) {
                            tagsEncontradas.add(tagRFID);
                        }
                    }
                } else if (responseCode == 404) {
                    synchronized (tagsNaoEncontradas) {
                        tagsNaoEncontradas.add(tagRFID);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG_LOG, "Erro API tag", e);
                synchronized (tagsNaoEncontradas) {
                    tagsNaoEncontradas.add(tagRFID);
                }
            }
        }).start();
    }



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
        if (mReader != null) {
            try { mReader.free(); } catch (Exception e) { Log.e(TAG_LOG, "Erro ao liberar leitor", e); }
        }
        if (readerThread != null) readerThread.quitSafely();
    }

    private static class TagAdapter extends BaseAdapter {
        private List<String> tags;
        private LayoutInflater inflater;

        public TagAdapter(Context context, List<String> tags) {
            this.tags = tags;
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() { return tags.size(); }

        @Override
        public Object getItem(int position) { return tags.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.imagem_rfid_tag, parent, false);
                holder = new ViewHolder();
                holder.tvTag = convertView.findViewById(R.id.tvTagText);
                holder.imgTag = convertView.findViewById(R.id.imgTag);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.tvTag.setText(tags.get(position));
            holder.imgTag.setImageResource(R.drawable.tagrfid);
            return convertView;
        }

        private static class ViewHolder {
            TextView tvTag;
            ImageView imgTag;
        }
    }
}