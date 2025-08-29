package com.example.uhf.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.AppData;
import com.example.uhf.R;
import com.example.uhf.activity.LogsActivity;
import com.example.uhf.activity.OperadoresActivity;
import com.example.uhf.activity.RetiradaActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

public class CadastroActivity extends AppCompatActivity {

    private ListView lvTagsErro;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro);

        lvTagsErro = findViewById(R.id.lvTagsErro);

        // Atualiza a lista ao iniciar
        atualizarListaErro();

        // Clique em uma tag abre a Activity de cadastro detalhado
        lvTagsErro.setOnItemClickListener((parent, view, position, id) -> {
            String tagSelecionada = AppData.listaTagsErro404.get(position);

            Intent intent = new Intent(CadastroActivity.this, CadastroDetalheActivity.class);
            intent.putExtra("tagRFID", tagSelecionada);
            startActivity(intent);
        });
    }

    private void atualizarListaErro() {
        // Adapter simples mostrando tags de erro
        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                AppData.listaTagsErro404
        );
        lvTagsErro.setAdapter(adapter);
    }

    // Se precisar adicionar tags de erro dinamicamente
    private void adicionarTagErro(String tag) {
        AppData.listaTagsErro404.add(tag);
        adapter.notifyDataSetChanged(); // atualiza a ListView
    }
}