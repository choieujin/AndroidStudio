package com.example.midtermapp;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.support.v4.content.PermissionChecker.PERMISSION_GRANTED;

public class PostActivity extends AppCompatActivity
    implements OnMapReadyCallback, GoogleMap.OnMapClickListener {

    Marker marker;
    public static final String POST_RESULT="com.example.midtermapp.post.RESULT";
    public static final int REQUEST_TAKE_ALBUM = 500;
    public static final int REQUEST_TAKE_CAMERA = 450;
    final int MY_PERMISSIONS_REQUEST_POST = 100;
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationClient;

    PickImageHelper ViewHelper = new PickImageHelper();

    boolean imgFlag = false;
    boolean locFlag = false;
    boolean titleFlag = false;
    boolean contentFlag = false;
    boolean flag = false;
    String timeStamp;
    LatLng curLoc;
    String curT;
    String curC;
    String curF;

    Uri photoURI = null;
    ImageView imageView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post);

        permissionCheck();

        try{
            mFusedLocationClient =
                    LocationServices.getFusedLocationProviderClient(this);
        }catch (SecurityException e){
            e.printStackTrace();
        }

        imageView = (ImageView)findViewById(R.id.imageView);
        SupportMapFragment mapFragment =
                (SupportMapFragment)getSupportFragmentManager()
                        .findFragmentById(R.id.map_post);
        mapFragment.getMapAsync(this);

    }

    public void setImg(View view){
        //이미지 set하기 버튼 클릭
        if(flag) {
            timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            ViewHelper.selectImage(PostActivity.this, timeStamp);
        }
        else{
            Toast.makeText(getApplicationContext(),"You need permissions",Toast.LENGTH_SHORT).show();
        }
    }
    void permissionCheck(){
        if ((ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PERMISSION_GRANTED)
            ||(ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED)
            ||(ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA
                            , Manifest.permission.WRITE_EXTERNAL_STORAGE
                            , Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_POST);
        }
        else {
            flag = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d("Post","onReauestPermissionResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == MY_PERMISSIONS_REQUEST_POST){
            flag = true;
            for(int num : grantResults){
                if(num != PERMISSION_GRANTED){
                    flag = false;
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode == RESULT_OK) {
            Uri imageUri = ViewHelper.getPickImageResultUri(this,data,timeStamp);
            String path = ViewHelper.getRealPathFromURI(this,imageUri);
            Log.d("이미지","imageUri : "+imageUri.getPath());
            Log.d("이미지","getRealPathFromURI : "+path);
            imgFlag = true;
            Bitmap image = null;
            try {
                image = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                curF = path;
                imageView.setImageBitmap(image);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true); // 지도 줌 컨트롤 가능
        mMap.getUiSettings().setCompassEnabled(true); // 지도 나침판 가능
        try {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
            getDeviceLocation();
        }catch (SecurityException e){
            e.printStackTrace();
        }
        mMap.setOnMapClickListener(this);
    }

    public void getDeviceLocation() {
        Log.d("location","getDeviceLocaiton");
        try {
            Task<Location> locationResult = mFusedLocationClient.getLastLocation();
            Log.d("location", "getLastLocation");
            locationResult.addOnCompleteListener(new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    Log.d("location", "onComplete");
                    if (task.isSuccessful()) {
                        Location location = (Location) task.getResult();
                        mMap.moveCamera(CameraUpdateFactory.newLatLng(
                                new LatLng(location.getLatitude(), location.getLongitude())
                        ));
                    }
                }
            });
        }catch (SecurityException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        //map click 시 위치 저장하기
        Log.d("POST","마커 : "+latLng);
        curLoc = new LatLng(latLng.latitude,latLng.longitude);
        if(locFlag == true){ //기존 marker 존재
            marker.setPosition(latLng);
            curLoc = latLng;
            Log.d("마커",""+curLoc);
        }else {
            curLoc = latLng;
            Log.d("마커",""+curLoc);
            locFlag = true;
            //marker 추가
            marker = mMap.addMarker(new MarkerOptions().position(latLng));
        }
    }

    public void sendIntent(View view){
        Log.d("PostActivity","sendIntent");
        EditText title = (EditText)findViewById(R.id.title_);
        EditText content = (EditText)findViewById(R.id.content_);
        if(title.getText().toString().getBytes().length > 0){
            titleFlag = true;
        }
        if(content.getText().toString().getBytes().length > 0){
            contentFlag = true;
        }
        if(!((imgFlag == true) && (locFlag==true)&&(titleFlag==true)&&(contentFlag==true))){
                //한개라도 false일 경우
            Toast.makeText(getApplicationContext(),"set all inputs(title,content,location,image)",Toast.LENGTH_SHORT).show();
        }
        else {
            //DB insert
            curT = title.getText().toString();
            curC = content.getText().toString();
            Database dbH = new Database(this);
            dbH.insert(curLoc,curT,curC,curF);

            Intent intent = new Intent();
            setResult(RESULT_OK,intent);
            finish();
            onBackPressed();
        }
    }
}
