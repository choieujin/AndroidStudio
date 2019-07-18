package com.example.midtermapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class Database extends SQLiteOpenHelper {

    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "Mdb.db";
    public static final String _TABLENAME = "itemTable";


    public Database(Context context) {
        super(context, DATABASE_NAME,null,DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d("디비","onCreate");
        db.execSQL("create table if not exists " + _TABLENAME + "("
                +"id"+" integer primary key autoincrement,"
                +"lat"+" double not null , "
                +"lon"+" double not null , "
                + "title" + " text not null , "
                + "content" + " text not null , "
                + "filepath" + " text not null "
                +");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
    }

    public void insert(LatLng _loc, String _title, String _content, String _path){
        Log.d("디비","insert "+_loc.latitude+"&"+_loc.longitude+", "+_title
        +", "+_content+", "+_path);
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("lat",_loc.latitude);
        values.put("lon",_loc.longitude);
        values.put("title",_title);
        values.put("content",_content);
        values.put("filepath",_path);
        Log.d("디비","lat, lon : "+values.get("lat")+", "+values.get("lon"));
        db.insertOrThrow(_TABLENAME,null,values);
        db.close();
    }

    public void delete(int _id) {
        Log.d("디비","delete : "+_id);
        SQLiteDatabase db = getWritableDatabase();
        // 입력한 항목과 일치하는 행 삭제
        db.delete(_TABLENAME,"id="+_id,null);
        db.close();
    }

    public Item getLast(){
        Log.d("디비","getLast");
        Item temp;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM "+_TABLENAME,null);
        cursor.moveToLast();
        Log.d("디비","lat : " + cursor.getDouble(cursor.getColumnIndex("lat")));
        Log.d("디비","lon : "+ cursor.getDouble(cursor.getColumnIndex("lon")));
        LatLng latLng = new LatLng(cursor.getDouble(cursor.getColumnIndex("lat")),
                cursor.getDouble(cursor.getColumnIndex("lon")));
        Log.d("DB","moveToLast"+cursor.getInt(0));
        temp = new Item(cursor.getString(cursor.getColumnIndex("title")),
                cursor.getString(cursor.getColumnIndex("content")),
                cursor.getString(cursor.getColumnIndex("filepath")),
                latLng);
        temp.Id = cursor.getInt(cursor.getColumnIndex("id"));
        Log.d("디비","temp.Id"+ temp.Id);
        return temp;
    }

    public List<Item> getResult(){
        Log.d("디비","getResult");
        List<Item> items = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Item temp;
        Cursor cursor = db.rawQuery("SELECT * FROM "+_TABLENAME,null);
        while(cursor.moveToNext()){
            LatLng latLng = new LatLng(cursor.getDouble(cursor.getColumnIndex("lat")),
                    cursor.getDouble(cursor.getColumnIndex("lon")));
            temp = new Item(cursor.getString(cursor.getColumnIndex("title")),
                    cursor.getString(cursor.getColumnIndex("content")),
                    cursor.getString(cursor.getColumnIndex("filepath")),
                    latLng
            );
            temp.Id = cursor.getInt(cursor.getColumnIndex("id"));
            items.add(temp);
        }
        return items;
    }

    public List<Item> getSearch(String keyword){
        Log.d("디비","getLast");
        List<Item> temp = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM "+_TABLENAME,null);
        Item cur = null;
        while(cursor.moveToNext()){
            Log.d("DB","Id : "+cursor.getInt(cursor.getColumnIndex("id")));
            if(cursor.getString(cursor.getColumnIndex("title")).equals(keyword)){
                LatLng latLng = new LatLng(cursor.getDouble(cursor.getColumnIndex("lat")),
                        cursor.getDouble(cursor.getColumnIndex("lon")));
                cur = new Item(cursor.getString(cursor.getColumnIndex("title")),
                        cursor.getString(cursor.getColumnIndex("content")),
                        cursor.getString(cursor.getColumnIndex("filepath")),
                        latLng);
                cur.Id = cursor.getInt(cursor.getColumnIndex("id"));
                Log.d("디비","find : "+cur.Id+", "+cur.title+","+cur.content);
                temp.add(cur);
            }
            else if(cursor.getString(cursor.getColumnIndex("content")).equals(keyword)){
                LatLng latLng = new LatLng(cursor.getDouble(cursor.getColumnIndex("lat")),
                        cursor.getDouble(cursor.getColumnIndex("lon")));
                cur = new Item(cursor.getString(cursor.getColumnIndex("title")),
                        cursor.getString(cursor.getColumnIndex("content")),
                        cursor.getString(cursor.getColumnIndex("filepath")),
                        latLng);
                cur.Id = cursor.getInt(cursor.getColumnIndex("id"));
                Log.d("디비","find : "+cur.Id+", "+cur.title+","+cur.content);
                temp.add(cur);
            }
        }
        return temp;
    }

}
