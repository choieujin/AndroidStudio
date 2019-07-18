package com.example.midtermapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;

import static android.app.Activity.RESULT_OK;

public class MapsFragment extends Fragment
        implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    GoogleMap mMap;
    Context context_;
    Marker cur_point;
    private MapView mapView = null;
    FusedLocationProviderClient mFusedLocationProviderClient;
    ArrayList<Marker> markers;
    public static final int REQUEST_DETAIL_RESULT = 2000;


    public MapsFragment() {
        // required
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        context_ = context;
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.fragment_maps, container, false);

        markers = new ArrayList<>();
        Log.d("MapsActivity","onCreateView");
        final Database dbH = new Database(getContext());
        MainActivity.items = dbH.getResult();

        mapView = (MapView) layout.findViewById(R.id.map);
        mapView.getMapAsync(this);

        return layout;
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onLowMemory();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) { // 구글맵이 준비가되면 호출되는 콜백
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true); // 지도 줌 컨트롤 가능
        mMap.getUiSettings().setCompassEnabled(true); // 지도 나침판 가능
        mMap.setOnMarkerClickListener(this);

        Log.d("맵","onMapReady item 개수 : "+MainActivity.items.size());
        int size = MainActivity.items.size();
        int index = 0;
        for(Item num : MainActivity.items){
            Log.d("맵","marker set"+num.loc);
            LatLng latLng = new LatLng(num.loc.latitude,num.loc.longitude);
            Marker tempMarker = mMap.addMarker(new MarkerOptions()        //마커 생성 (마커를 나타냄)
                    .position(latLng)
                    .title(index+""));
            Log.d("맵","marker add and title is "+tempMarker.getTitle());
            markers.add(tempMarker);
            index++;
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        Log.d("맵","onMarkerClick");
        int _id = Integer.parseInt(marker.getTitle()); //string -> int
        Log.d("맵","id : "+_id);
        Intent detailIntent = new Intent(getActivity(),DetailActivity.class);
        DetailActivity.clickItem = MainActivity.items.get(_id);
        DetailActivity.clickPos = _id;
        cur_point = marker;
        startActivityForResult(detailIntent,REQUEST_DETAIL_RESULT);
        return false;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("메인", "onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
        //data를 list<item>에 추가.
        switch (requestCode) {
            case REQUEST_DETAIL_RESULT:
                if (resultCode == RESULT_OK) {
                    cur_point.remove();
                }
                break;
        }
    }
}