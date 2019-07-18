package com.example.midtermapp;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;

public class Item implements Serializable {
    public int Id;
    public String title;
    public String content;
    public String filepath;
    public LatLng loc;

    public Item(String _title,String _content,String _file, LatLng _loc){
        title = _title;
        content = _content;
        filepath = _file;
        loc = _loc;
    }
}