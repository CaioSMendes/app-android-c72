package com.example.uhf.activity;

import android.content.Context;
import android.content.Intent;
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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

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

    private RFIDWithUHFUART mReader;
    private boolean isReading = false;

    private List<String> listaTags = new ArrayList<>();
    private Set<String> tagsLidas = new HashSet<>();
    private TagAdapter adapter;

    private Handler handler = new Handler();
    private Runnable leituraRunnable;

    private ToneGenerator toneGen;

    private HandlerThread readerThread;
    private Handler readerHandler;

    private boolean modoSingle = false; // Loop como padrão
    private Button rbSingle, rbLoop;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leitura_tag);

        btnInventory = findViewById(R.id.btnStartInventory);
        btnClearTags = findViewById(R.id.btnClearTags);
        tvTagCount = findViewById(R.id.tvTagCount);
        lvTags = findViewById(R.id.lvTags);
        cbFilter = findViewById(R.id.cbFilter);
        rbSingle = findViewById(R.id.rbSingle);
        rbLoop = findViewById(R.id.rbLoop);

        // Inicializa o visual dos botões conforme o modo padrão

        // Clique em "Single"
        rbSingle.setOnClickListener(v -> {
            modoSingle = true;
        });

        // Clique em "Loop"
        rbLoop.setOnClickListener(v -> {
            modoSingle = false;
        });

        adapter = new TagAdapter(this, listaTags);
        lvTags.setAdapter(adapter);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // Inicializa leitor em background
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
            listaTags.clear();
            tagsLidas.clear();
            adapter.notifyDataSetChanged();
            tvTagCount.setText("0");
        });
    }

    private void inicializarLeitor() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
            if (mReader.init(this)) {
                Log.i(TAG_LOG, "Leitor RFID inicializado com sucesso.");
                runOnUiThread(() ->
                        Toast.makeText(this, "Leitor RFID inicializado.", Toast.LENGTH_SHORT).show()
                );
            } else {
                Log.e(TAG_LOG, "Falha ao inicializar leitor.");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Erro ao inicializar leitor.", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
            try { mReader.stopInventory(); } catch (Exception ignored) {}
        } catch (Exception e) {
            Log.e(TAG_LOG, "Erro ao inicializar UHF: " + e.getMessage(), e);
            runOnUiThread(() -> {
                Toast.makeText(this, "Erro crítico ao inicializar leitor.", Toast.LENGTH_LONG).show();
                finish();
            });
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
        adapter.notifyDataSetChanged();
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

                            // Pega primeiros 7 caracteres da EPC
                            String primeiros7 = epc.length() >= 6 ? epc.substring(0, 6) : epc;
                            String valorExibir;

                            // Verifica se contém letras (A-F)
                            if (primeiros7.matches("[0-9]+")) {
                                // Só números → decimal, mostra os 7 primeiros
                                valorExibir = primeiros7;
                            } else {
                                // Contém letras → trata como hexadecimal e converte para decimal
                                try {
                                    long decimal = Long.parseLong(primeiros7, 16);
                                    valorExibir = String.valueOf(decimal);
                                } catch (NumberFormatException e) {
                                    // Se não der para converter, mostra raw
                                    valorExibir = primeiros7;
                                }
                            }

                            listaTags.add(valorExibir);
                            adapter.notifyDataSetChanged();
                            tvTagCount.setText(String.valueOf(tagsLidas.size()));

                            // Som ao ler tag
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int triggerKeyCode = 293; // Código do gatilho do C72
        Log.d(TAG_LOG, "Key event: " + event.getKeyCode() + " action: " + event.getAction());

        if (event.getKeyCode() == triggerKeyCode) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                Log.d(TAG_LOG, "Gatilho pressionado!");

                if (modoSingle) {
                    // Single: lê uma vez
                    if (!isReading) {
                        iniciarLeitura();
                        // Para logo após a leitura
                        handler.postDelayed(this::pararLeitura, 200); // 200ms para garantir leitura
                    }
                } else {
                    // Loop: inicia leitura contínua
                    if (!isReading) iniciarLeitura();
                }

            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                Log.d(TAG_LOG, "Gatilho solto!");
                // Para leitura contínua se estiver em Loop
                if (!modoSingle && isReading) {
                    pararLeitura();
                }
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
            try {
                mReader.free();
            } catch (Exception e) {
                Log.e(TAG_LOG, "Erro ao liberar leitor: " + e.getMessage());
            }
        }
        if (readerThread != null) readerThread.quitSafely();
    }

    // Adapter para ListView
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
                //convertView = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
                convertView = inflater.inflate(R.layout.imagem_rfid_tag, parent, false);
                holder = new ViewHolder();
                //holder.tvTag = convertView.findViewById(android.R.id.text1);
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