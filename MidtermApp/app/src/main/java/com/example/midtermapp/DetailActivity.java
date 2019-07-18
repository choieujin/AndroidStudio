package com.example.midtermapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.kakao.kakaolink.v2.KakaoLinkResponse;
import com.kakao.kakaolink.v2.KakaoLinkService;
import com.kakao.message.template.ContentObject;
import com.kakao.message.template.FeedTemplate;
import com.kakao.message.template.LinkObject;
import com.kakao.message.template.LocationTemplate;
import com.kakao.network.ErrorResult;
import com.kakao.network.callback.ResponseCallback;
import com.kakao.util.helper.log.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DetailActivity extends AppCompatActivity
        implements  OnMapReadyCallback {
    static Item clickItem;
    static int clickPos;
    GoogleMap mMap;
    Button deleteB;
    Button shareB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        Log.d("디테일",""+clickItem.Id);
        deleteB = findViewById(R.id.deleteD);
        shareB = findViewById(R.id.shareD);
        deleteB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteItem();
            }
        });
        shareB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareItem();
            }
        });
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_detail);
        mapFragment.getMapAsync(this);
        try {
            Log.d("리스트","이미지 넣기");
            File file = new File(clickItem.filepath);
            Uri imageUri = Uri.fromFile(file);
            Log.d("리스트_이미지", "" + imageUri.getPath());
            Bitmap image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);

            //썸네일 만들기 -> 이미지 크기 줄이기
            Bitmap ThumbImage = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(clickItem.filepath), 160, 160);
            ((ImageView)findViewById(R.id.imageView)).setImageBitmap(ThumbImage); //img set
        }catch (IOException e){
            e.printStackTrace();
        }
        ((TextView)findViewById(R.id.titleD)).setText(clickItem.title);
        ((TextView)findViewById(R.id.contentD)).setText(clickItem.content);
    }

    public void deleteItem(){
        Log.d("디테일","deleteItem : " + clickItem.Id);
        Database dbH = new Database(this);
        dbH.delete(clickItem.Id);

        Log.d("삭제","delete in list");
        MainActivity.items.remove(clickPos);
        ListFragment.adapter.notifyDataSetChanged();
        Intent intent = new Intent();
        setResult(RESULT_OK,intent);
        finish();
        onBackPressed();
    }


    public void shareItem(){
        Log.d("디테일","shareItem");
        String addr = getAddress(clickItem.loc.latitude,clickItem.loc.longitude);

        Log.d("디테일","주소 : "+addr);

        LocationTemplate params = LocationTemplate.newBuilder(addr,
                ContentObject.newBuilder(clickItem.title,
                        "http://www.kakaocorp.com/images/logo/og_daumkakao_151001.png",
                        LinkObject.newBuilder()
                                .setWebUrl("https://developers.kakao.com")
                                .setMobileWebUrl("https://developers.kakao.com")
                                .build())
                        .setDescrption(clickItem.content)
                        .build())
                .setAddressTitle(clickItem.title)
                .build();

        Map<String, String> serverCallbackArgs = new HashMap<String, String>();
        serverCallbackArgs.put("user_id", "${current_user_id}");
        serverCallbackArgs.put("product_id", "${shared_product_id}");

        KakaoLinkService.getInstance().sendDefault(this, params, serverCallbackArgs, new ResponseCallback<KakaoLinkResponse>() {
            @Override
            public void onFailure(ErrorResult errorResult) {
                Logger.e(errorResult.toString());
            }

            @Override
            public void onSuccess(KakaoLinkResponse result) {
                // 템플릿 밸리데이션과 쿼터 체크가 성공적으로 끝남. 톡에서 정상적으로 보내졌는지 보장은 할 수 없다. 전송 성공 유무는 서버콜백 기능을 이용하여야 한다.
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap=googleMap;
        mMap.getUiSettings().setCompassEnabled(true); // 지도 나침판 가능

        Log.d("디테일","onMapReady item : "+clickItem.title);

        LatLng latLng = new LatLng(clickItem.loc.latitude,clickItem.loc.longitude);
        Log.d("디테일","marker : "+latLng);
        mMap.addMarker(new MarkerOptions()        //마커 생성 (마커를 나타냄)
                .position(latLng));

        mMap.moveCamera(CameraUpdateFactory.newLatLng(clickItem.loc));

    }



    public String getAddress(double lat, double lng){
        Log.d("디테일","getAddress : "+lat+", "+lng);
        String address = null;
        Geocoder geoCoder = new Geocoder(this);
        List<Address> list = null;
        try {
            list = geoCoder.getFromLocation(lat,lng,1);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(list == null){
            Log.e("주소","주소데이터 얻기 실패");
            Toast.makeText(getApplicationContext(),"주소를 얻지 못하였습니다",Toast.LENGTH_SHORT).show();
            return null;
        }
        if(list.size() == 0){
            Log.e("주소","해당되는 주소 정보가 없습니다");
            Toast.makeText(getApplicationContext(),"주소를 얻지 못하였습니다",Toast.LENGTH_SHORT).show();
            return null;
        }
        if(list.size() > 0){
            Log.d("디테일",""+list.get(0));
            Address addr = list.get(0);
            Log.d("디테일","getAddressLine : "+addr.getAddressLine(0));
            address = addr.getAddressLine(0);
        }
        Log.d("디테일","주소 : "+address);
        return address;
    }
}

