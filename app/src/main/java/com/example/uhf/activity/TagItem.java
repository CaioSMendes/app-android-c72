package com.example.uhf.activity;

public class TagItem {
    public String tagRFID;
    public String objeto;
    public String idInterno;

    public TagItem(String tagRFID, String objeto, String idInterno) {
        this.tagRFID = tagRFID;
        this.objeto = objeto;
        this.idInterno = idInterno;
    }
}