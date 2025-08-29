package com.example.uhf.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

public class HomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        LinearLayout btnConfiguracao = findViewById(R.id.btnConfiguracao);
        LinearLayout btnRetirada = findViewById(R.id.btnRetiradaHome);
        LinearLayout btnEntrega = findViewById(R.id.btnEntrega);
        //LinearLayout btnCadastro = findViewById(R.id.btnCadastro); // aqui vai pro cadastro antigo
        LinearLayout btnCadastro = findViewById(R.id.btnCadastro);


        btnConfiguracao.setOnClickListener(v -> {
            Intent intent = new Intent(this, SetupActivity.class);
            startActivity(intent);
        });

        btnRetirada.setOnClickListener(v -> {
            Intent intent = new Intent(this, LeituraTagActivity.class);
            intent.putExtra("acao", "retirada");
            startActivity(intent);
        });

        btnEntrega.setOnClickListener(v -> {
            Intent intent = new Intent(this, LeituraTagActivity.class);
            intent.putExtra("acao", "entrega");
            startActivity(intent);
        });

        btnCadastro.setOnClickListener(v -> {
            Intent intent = new Intent(this, LeituraTagActivity.class);
            intent.putExtra("acao", "cadastro");
            startActivity(intent);
        });
    }
}
