package com.example.uhf.activity;

public class TagItem {
    public String tagRFID;
    public String objeto;
    public String idInterno;
    public boolean selecionado;
    private String empty; // "1" ou "0"


    public TagItem(String tagRFID, String objeto, String idInterno) {
        this.tagRFID = tagRFID;
        this.objeto = objeto;
        this.idInterno = idInterno;
        this.selecionado = false;
        this.empty = empty;
    }

    public String getTagRFID() {
        return tagRFID;
    }

    public String getObjeto() {
        return objeto;
    }

    public String getIdInterno() {
        return idInterno;
    }

    public boolean isSelecionado() {
        return selecionado;
    }

    public void setSelecionado(boolean selecionado) {
        this.selecionado = selecionado;
    }

    public String getEmpty() { return empty; }

}