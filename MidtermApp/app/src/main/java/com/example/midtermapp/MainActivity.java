package com.example.midtermapp;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static android.support.v4.content.PermissionChecker.PERMISSION_GRANTED;
import static com.kakao.util.helper.Utility.getPackageInfo;

public class MainActivity extends AppCompatActivity{

    public static final int REQUEST_POST_RESULT = 1000;
    final int MY_PERMISSIONS_REQUEST_LOCATION = 150;

    boolean permisionL = false;

    static List<Item> items = new ArrayList<>();
    ListFragment listFragment = new ListFragment();
    MapsFragment mapsActivity = new MapsFragment();
    LatLng cur_loc = null;
    static Database dbH;
    int frNum;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    //두번째 인자는 fragement 가 stack에 쌓일지 말지 여부
                    frNum = 0;
                    addFragment(new ListFragment(), false, "one");
                    //listFragment.setList(dbH.getResult());
                    return true;
                case R.id.navigation_dashboard:
                    frNum = 1;
                    addFragment(new MapsFragment(), false, "two");
                    //mapsActivity.setList(dbH.getResult());
                    return true;
                case R.id.navigation_notifications:
                    frNum=2;
                    addFragment(new SearchFragment(), false, "two");
                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        permissionCheckLocation();

        dbH = new Database(MainActivity.this);

        BottomNavigationView navigation =
                (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        items = dbH.getResult();
        FloatingActionButton fab = findViewById(R.id.addItem);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(permisionL) {
                    //권한 체크
                    Intent addItemIntent = new Intent(v.getContext(), PostActivity.class);
                    //this, 를 썻더니 cannot resolve constructor 오류가 발생 -> v.getContext로 변경
                    startActivityForResult(addItemIntent, REQUEST_POST_RESULT);
                }
                else{
                    Toast.makeText(getApplicationContext(),"need permission location",Toast.LENGTH_SHORT).show();
                }
            }
        });
        addFragment(new ListFragment(), false, "one");
    }

    void permissionCheckLocation(){
        Log.d("메인","permissionCheckLocation");
        if ((ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED)) {
            Log.d("메인","permission location ok");
            permisionL = true;
        } else  {
            Log.d("메인","permission request ing");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d("메인","onRequestPermissionResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case MY_PERMISSIONS_REQUEST_LOCATION:
                if(grantResults.length > 0
                        && grantResults[0] == PERMISSION_GRANTED
                        && grantResults[1] == PERMISSION_GRANTED) {
                    Log.d("메인","onRequestPermissionResult :"+permissions[0]+":"+grantResults[0]);
                    Log.d("메인","onRequestPermissionResult :"+permissions[1]+":"+grantResults[1]);
                    permisionL = true;
                }
                else
                if ((ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED)
                        || (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED)) {
                    Log.d("메인","permission location ok");
                    permisionL = true;
                }else{
                    Toast.makeText(getApplicationContext(),"need permission location",Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("메인", "onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
        //data를 list<item>에 추가.
        switch (requestCode) {
            case REQUEST_POST_RESULT:
                if (resultCode == RESULT_OK) {
                    Item temp = dbH.getLast(); //방금 전 Post에서 추가 된 data 가져오기
                    Log.d("메인", "dbH.getLast Item : " + temp.Id + ", " + temp.filepath + ", " + temp.content
                            + ", " + temp.title + ", " + temp.loc);
                    items.add(temp);
                    switch (frNum){
                        case 0:
                            addFragment(new ListFragment(), false, "one");
                            break;
                        case 1:
                            addFragment(new MapsFragment(), false, "two");
                            break;
                        case 2:
                            addFragment(new SearchFragment(), false, "two");
                            break;
                    }
                }
                break;
        }
    }

    public void addFragment(Fragment fragment, boolean addToBackStack, String tag) {
        Log.d("메인", "addFragment");
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction ft = manager.beginTransaction();

        if (addToBackStack) {
            ft.addToBackStack(tag);
        }
        ft.replace(R.id.container_frame, fragment, tag);
        ft.commitAllowingStateLoss();
    }

}

