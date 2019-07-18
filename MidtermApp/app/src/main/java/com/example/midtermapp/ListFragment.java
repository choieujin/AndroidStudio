package com.example.midtermapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;


public class ListFragment extends Fragment {
    ListView listView;
    static ItemAdapter adapter;

    public static final int REQUEST_DETAIL_RESULT = 2000;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d("ListFragment","onCreateView");
        // Inflate the layout for this fragment

        adapter = new ItemAdapter(getActivity(),MainActivity.items);

        final View view = inflater.inflate(R.layout.fragment_list,container,false);
        listView = (ListView)view.findViewById(R.id.item);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent detailIntent = new Intent(getActivity(),DetailActivity.class);
                DetailActivity.clickItem = MainActivity.items.get(position);
                DetailActivity.clickPos = position;
                startActivityForResult(detailIntent,REQUEST_DETAIL_RESULT);
            }
        });
        return view;
    }

    @Override
    public void onAttach(Context context) {
        Log.d("ListFragment","onAttach");
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

}
