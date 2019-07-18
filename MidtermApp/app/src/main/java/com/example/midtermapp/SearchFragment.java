package com.example.midtermapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class SearchFragment extends Fragment {

    ListView searchItems;
    List<Item> findItems = new ArrayList<>();
    ItemAdapter adapter;
    EditText text;

    String keyword;
    public SearchFragment() {
        // Required empty public constructor
    }

    void search(String keyword){
        Log.d("검색","keyword : "+keyword);
        findItems.clear();
        findItems.addAll(MainActivity.dbH.getSearch(keyword));
        Log.d("검색","find 개수 : "+findItems.size());
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_search, container, false);
        searchItems = (ListView)view.findViewById(R.id.searchItems);
        text = (EditText)view.findViewById(R.id.searchText);
        adapter = new ItemAdapter(getActivity(),findItems);
        searchItems.setAdapter(adapter);
        FloatingActionButton fab = view.findViewById(R.id.searchBtn);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                keyword = text.getText().toString();
                search(keyword);
            }
        });
        return view;
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
