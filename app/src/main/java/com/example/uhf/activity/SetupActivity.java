package com.example.uhf.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

public class SetupActivity extends AppCompatActivity {

    private EditText edtEmail, edtSenha, edtSerial, edtBaseUrl;
    private Button btnSalvar;
    private TextView txtDadosSalvos;

    private SharedPreferences prefs;
    private final String PREFS_NAME = "SetupPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup); // conecta ao XML

        // Inicializando SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Referenciando os elementos do layout
        edtEmail = findViewById(R.id.edtEmail);
        edtSenha = findViewById(R.id.edtSenha);
        edtSerial = findViewById(R.id.edtSerial);
        edtBaseUrl = findViewById(R.id.edtBaseUrl);
        btnSalvar = findViewById(R.id.btnSalvar);
        txtDadosSalvos = findViewById(R.id.txtDadosSalvos);

        // Carregar os dados salvos ao abrir a activity
        edtEmail.setText(prefs.getString("email", ""));
        edtSenha.setText(prefs.getString("senha", ""));
        edtSerial.setText(prefs.getString("serial", ""));
        edtBaseUrl.setText(prefs.getString("baseUrl", ""));
        txtDadosSalvos.setText(getDadosSalvosMasked());

        // Salvar os dados ao clicar no botão
        btnSalvar.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("email", edtEmail.getText().toString());
            editor.putString("senha", edtSenha.getText().toString());
            editor.putString("serial", edtSerial.getText().toString());
            editor.putString("baseUrl", edtBaseUrl.getText().toString());
            editor.apply(); // salva de forma assíncrona

            txtDadosSalvos.setText(getDadosSalvosMasked());
        });
    }

    // Função auxiliar para montar a string exibida no TextView, com senha mascarada
    private String getDadosSalvosMasked() {
        String senha = prefs.getString("senha", "");
        String senhaMascarada = senha.replaceAll(".", "*"); // substitui cada caractere por *

        return "Email: " + prefs.getString("email", "") +
                "\nSenha: " + senhaMascarada +
                "\nSerial: " + prefs.getString("serial", "") +
                "\nBase URL: " + prefs.getString("baseUrl", "");
    }
}