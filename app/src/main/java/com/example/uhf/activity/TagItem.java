package com.example.uhf.activity;

public class TagItem {
    public String tagRFID;
    public String objeto;
    public String idInterno;
    public boolean selecionado;

    public TagItem(String tagRFID, String objeto, String idInterno) {
        this.tagRFID = tagRFID;
        this.objeto = objeto;
        this.idInterno = idInterno;
        this.selecionado = false;
    }

    // Getter para compatibilidade
    public String getIdTag() {
        return idInterno; // ou return tagRFID; dependendo do que você precisa
    }

    public boolean isSelecionado() {
        return selecionado;
    }

    public void setSelecionado(boolean selecionado) {
        this.selecionado = selecionado;
    }

}