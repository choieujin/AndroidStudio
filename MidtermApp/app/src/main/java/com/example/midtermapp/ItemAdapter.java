package com.example.midtermapp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;


public class ItemAdapter extends BaseAdapter {

    private List<Item> list;
    private Activity context;

    public ItemAdapter(Activity _context, List<Item> _list ){
        list = _list;
        context = _context;
    }
    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Log.d("리스트"," : "+list.get(position).Id);
        Context context = parent.getContext();
        View view = convertView;
        if(view == null){
            view = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.item_list,parent,false
            );
        }
        File file = new File(list.get(position).filepath);
        Uri imageUri = Uri.fromFile(file);
        Log.d("리스트_이미지", "" + imageUri.getPath());
        //썸네일 만들기 -> 이미지 크기 줄이기
        Bitmap ThumbImage = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(list.get(position).filepath), 160, 160);
        ((ImageView)view.findViewById(R.id.img)).setImageBitmap(ThumbImage); //img set
        ((TextView)view.findViewById(R.id.title_text)).setText(list.get(position).title);
        ((TextView)view.findViewById(R.id.content_text)).setText(list.get(position).content);
        return view;
    }
}

