package com.example.mysolarsystem;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;

import com.example.mysolarsystem.ObjParser;
import com.example.mysolarsystem.ObjStructure;

public class SolarSystemRenderer implements GLSurfaceView.Renderer {
    private Context context;

    /* For obj(planet) & texture */
    private ObjStructure[] planet = new ObjStructure[10];

    public int[] texture_id = new int[]{    // 태양, 수성, 금성, 지구, 달, 화성, 목성, 토성, 천왕성, 해왕성
            R.drawable.sun,R.drawable.mercury,R.drawable.venus,
            R.drawable.earth,R.drawable.moon,R.drawable.mars,
            R.drawable.jupiter, R.drawable.saturn, R.drawable.uranus,
            R.drawable.neptune};
    public float scaler = .5f;  // 태양 크기 결정
    public float[] scalefactor = new float[]{   // 태양으로부터 상대적 크기 결정
            scaler, scaler*0.1f, scaler*0.2f,   // 태양, 수성, 금성
            scaler*0.25f, scaler*0.08f, scaler*0.18f,    // 지구, 달, 화성
            scaler*0.5f, scaler*0.4f,scaler*0.3f,scaler*0.3f};  // 목성, 토성, 천왕성, 해왕성

    /* For rotation */
    public boolean rot_flag = true;
    private float rot_sun = 360.0f;
    private float angle_mercury = 0.0f;
    private float angle_venus = 0.0f;
    private float angle_earth = 0.0f;
    private float angle_mars = 0.0f;
    private float angle_jupiter = 0.0f;
    private float angle_saturn = 0.0f;
    private float angle_uranus = 0.0f;
    private float angle_neptune = 0.0f;

    private float orbital_earth = 1.0f;
    private float orbital_mercury = 2.0f;
    private float orbital_venus = 1.5f;
    private float orbital_mars = 0.9f;
    private float orbital_jupiter = 0.5f;
    private float orbital_saturn = 0.3f;
    private float orbital_uranus = 0.2f;
    private float orbital_neptune = 0.18f;

    /*for light*/
    private float[] ambient = {0.2f, 0.2f, 0.2f, 1.0f};
    private float[] diffuse = {0.8f, 0.8f, 0.8f, 1.0f};
    private float[] specular = {0.8f, 0.8f, 0.8f, 1.0f};

    private final int SUN_LIGHT = GL10.GL_LIGHT0;
    private float[] sun_ambient = {1.0f, 0.0f, 0.0f, 1.0f};
    private float[] sun_diffuse = {1.0f, 1.0f, 1.0f, 1.0f};
    private float[] sun_specular = {1.0f, 0.0f, 0.0f, 1.0f};

    private float[] earth_ambient = {0.0f, 0.0f, 1.0f, 1.0f};
    private float[] earth_diffuse = {0.5f, 0.5f, 0.5f, 1.0f};
    private float[] earth_specular = {0.5f, 0.5f, 0.5f, 1.0f};

    private float[] moon_ambient = {1.0f, 1.0f, 0.0f, 1.0f};
    private float[] moon_diffuse = {0.5f, 0.5f, 0.5f, 1.0f};
    private float[] moon_specular = {0.5f, 0.5f, 0.0f, 1.0f};

    /* For camera setting */
    private double distance;
    public volatile double elev;
    public volatile double azim;

    private float[] cam_eye = new float[3];
    private float[] cam_center = new float[3];
    private float[] cam_up = new float[3];
    private float[] cam_vpn = new float[3];
    private float[] cam_x_axis = new float[3];

    private float[] uv_py = new float[3];
    private float[] uv_ny = new float[3];

    /* For texture on, off */
    public boolean texture_on_off = true;

    public SolarSystemRenderer(Context context) {
        this.context = context;
    }

    private static FloatBuffer makeFloatBuffer(float[] arr){
        ByteBuffer bb = ByteBuffer.allocateDirect(arr.length*4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(arr);
        fb.position(0);
        return fb;
    }
    private void addLight(GL10 gl, int Light_ID, float[] ambient, float[] diff, float[] spec, float[] pos){
        gl.glLightfv(Light_ID, GL10.GL_POSITION, makeFloatBuffer(pos));
        gl.glLightfv(Light_ID, GL10.GL_AMBIENT, makeFloatBuffer(ambient));
        gl.glLightfv(Light_ID, GL10.GL_DIFFUSE, makeFloatBuffer(diff));
        gl.glLightfv(Light_ID, GL10.GL_SPECULAR, makeFloatBuffer(spec));
        gl.glShadeModel(GL10.GL_SMOOTH); // GL_FLAT : 폴리곤에 단색을 입힘, GL_SMOOTH : 각 정점에 해당하는 색상 혼합
        gl.glEnable(Light_ID);
    }

    private void initMaterial(GL10 gl, float[] ambient, float[] diff, float[] spec, float shine) {
        gl.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_AMBIENT, makeFloatBuffer(ambient));
        gl.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_DIFFUSE, makeFloatBuffer(diff));
    }

    private void calcCross(float[] vector1, float[] vector2, float[] cp_vector) {
        cp_vector[0] = vector1[1] * vector2[2] - vector1[2] * vector2[1];
        cp_vector[1] = vector1[2] * vector2[0] - vector1[0] * vector2[2];
        cp_vector[2] = vector1[0] * vector2[1] - vector1[1] * vector2[0];
    }

    private void vNorm(float[] vector) {
        float scale = (float) Math.sqrt(Math.pow((double) vector[0], 2) + Math.pow((double) vector[1], 2) + Math.pow((double) vector[2], 2));

        vector[0] = vector[0] / scale;
        vector[1] = vector[1] / scale;
        vector[2] = vector[2] / scale;
    }

    private void calcUpVector() {
        double r_elev = elev * Math.PI / 180.0;
        double r_azim = azim * Math.PI / 180.0;

        cam_eye[0] = (float) distance * (float) Math.sin(r_elev) * (float) Math.sin(r_azim);
        cam_eye[1] = (float) distance * (float) Math.cos(r_elev);
        cam_eye[2] = (float) distance * (float) Math.sin(r_elev) * (float) Math.cos(r_azim);

        cam_vpn[0] = cam_eye[0] - cam_center[0];
        cam_vpn[1] = cam_eye[1] - cam_center[1];
        cam_vpn[2] = cam_eye[2] - cam_center[2];
        vNorm(cam_vpn);

        if (elev >= 0 && elev < 180) {
            calcCross(uv_py, cam_vpn, cam_x_axis); // 유사upVecotr와 vpn 외적
        }
        else {
            calcCross(uv_ny, cam_vpn, cam_x_axis); // 유사upVector가 반대방향인 경우
        }
        calcCross(cam_vpn, cam_x_axis, cam_up);
        vNorm(cam_up);
    }

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d("나","onSurfaceCreated");
        gl.glHint(gl.GL_PERSPECTIVE_CORRECTION_HINT, gl.GL_FASTEST);
        gl.glEnable(gl.GL_DEPTH_TEST);
        gl.glEnable(gl.GL_CULL_FACE);
        gl.glCullFace(gl.GL_BACK);

        gl.glEnable(gl.GL_TEXTURE);
        gl.glTexEnvf(gl.GL_TEXTURE_ENV,GL10.GL_TEXTURE_ENV_MODE, GL10.GL_MODULATE);

        gl.glEnable(gl.GL_LIGHTING);
        gl.glEnable(gl.GL_COLOR_MATERIAL);

        distance = 10.0;
        elev = 90.0;
        azim = 0.0;

        uv_py[0] = 0.0f;
        uv_py[1] = 1.0f;
        uv_py[2] = 0.0f;

        uv_ny[0] = 0.0f;
        uv_ny[1] = -1.0f;
        uv_ny[2] = 0.0f;

        cam_center[0] = 0.0f;
        cam_center[1] = 0.0f;
        cam_center[2] = 0.0f;

        calcUpVector();

        for(int i=0; i<10;i ++){
            ObjParser objParser = new ObjParser(context); // obj 파일 Parser 생성
            try {
                objParser.parse(R.raw.planet); // obj 파일 parsing

            } catch (IOException e) {

            }
            int group = objParser.getObjectIds().size(); // 몇 개의 obj 파일이 있는지 확인
            int[] texture = new int[group];
            texture[0] = texture_id[i]; // texture 파일 설정

            planet[i] = new ObjStructure(objParser, gl, this.context, texture); // objstructure 생성
        }
        gl.glEnable(GL10.GL_DEPTH_TEST);
    }

    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d("나","onSurfaceChanged");
        float zNear = 0.1f;
        float zFar = 1000f;
        float fovy = 45.0f;
        float aspect = (float) width / (float) height;

        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();

        GLU.gluPerspective(gl, fovy, aspect, zNear, zFar);
        gl.glViewport(0, 0, width, height);
    }

    public void onDrawFrame(GL10 gl) {
        Log.d("나","onDrawFrame");
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.glClearDepthf(1.0f);

        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();

        calcUpVector();

        GLU.gluLookAt(gl, cam_eye[0], cam_eye[1], cam_eye[2], cam_center[0], cam_center[1], cam_center[2], cam_up[0], cam_up[1], cam_up[2]);

        if(texture_on_off){
            gl.glEnable(GL10.GL_TEXTURE_2D);
        }else{
            gl.glDisable(GL10.GL_TEXTURE_2D);
        }

/*        R.drawable.sun,R.drawable.mercury,R.drawable.venus,
                R.drawable.earth,R.drawable.moon,R.drawable.mars,
                R.drawable.jupiter, R.drawable.saturn, R.drawable.uranus,
                R.drawable.neptune};*/
        gl.glColor4f(1.0f,1.0f,1.0f,1.0f);
        addLight(gl, SUN_LIGHT, ambient, diffuse, specular, new float[]{0.f, 0.f, 0.f, 1.f});

        gl.glPushMatrix();
            gl.glRotatef(rot_sun, 0.0f, 1.0f, 0.0f); // 태양의 자전
            // draw Sun
            gl.glLightModelf(GL10.GL_LIGHT_MODEL_TWO_SIDE,1.f);
            initMaterial(gl, sun_ambient, sun_diffuse,sun_specular,10.0f);
            planet[0].setScale(scalefactor[0]);
            planet[0].draw(gl);
        gl.glPopMatrix();

        gl.glPushMatrix();
            gl.glRotatef(angle_mercury, 0.0f, 1.0f, 0.0f);
            gl.glTranslatef(2.0f, 0.0f, 0.0f);
            // draw mercury
            planet[1].setScale(scalefactor[1]);
            planet[1].draw(gl);
        gl.glPopMatrix();

        gl.glPushMatrix();
            gl.glRotatef(angle_venus, 0.0f, 1.0f, 0.0f);
            gl.glTranslatef(3.0f, 0.0f, 0.0f);
            // draw venus
            planet[2].setScale(scalefactor[2]);
            planet[2].draw(gl);
        gl.glPopMatrix();

        gl.glPushMatrix();
            gl.glRotatef(angle_earth, 0.0f, 1.1f, 0.0f); //회전 -> x축방향 변화
            gl.glTranslatef(4.0f, 0.0f, 0.0f); //x축방향으로 4만큼 이동 : 공전
            gl.glLightModelf(GL10.GL_LIGHT_MODEL_TWO_SIDE, 1.f);
            initMaterial(gl, earth_ambient, earth_diffuse, earth_specular, 10.0f);
            // draw Earth
            planet[3].setScale(scalefactor[3]);
            planet[3].draw(gl);

            gl.glPushMatrix();
                gl.glRotatef(angle_earth, 0.0f, 1.1f, 0.0f);
                gl.glTranslatef(.5f, 0.0f, 0.0f);
                gl.glLightModelf(GL10.GL_LIGHT_MODEL_TWO_SIDE,1.f);
                initMaterial(gl, moon_ambient, moon_diffuse,moon_specular,10.0f);
                // draw moon
                planet[4].setScale(scalefactor[4]);
                planet[4].draw(gl);
            gl.glPopMatrix();
        gl.glPopMatrix();

        gl.glPushMatrix();
            gl.glRotatef(angle_mars, 0.0f, 1.0f, 0.0f);
            gl.glTranslatef(5.0f, 0.0f, 0.0f);
            // draw mars
            planet[5].setScale(scalefactor[5]);
            planet[5].draw(gl);
        gl.glPopMatrix();

        gl.glPushMatrix();
            gl.glRotatef(angle_jupiter, 0.0f, 1.0f, 0.0f);
            gl.glTranslatef(6.0f, 0.0f, 0.0f);
            // draw jupiter
            planet[6].setScale(scalefactor[6]);
            planet[6].draw(gl);
        gl.glPopMatrix();

        gl.glPushMatrix();
            gl.glRotatef(angle_saturn, 0.0f, 1.0f, 0.0f);
            gl.glTranslatef(7.5f, 0.0f, 0.0f);
            // draw saturn
            planet[7].setScale(scalefactor[7]);
            planet[7].draw(gl);
        gl.glPopMatrix();

        gl.glPushMatrix();
            gl.glRotatef(angle_uranus, 0.0f, 1.0f, 0.0f);
            gl.glTranslatef(8.5f, 0.0f, 0.0f);
            // draw uranus
            planet[8].setScale(scalefactor[8]);
            planet[8].draw(gl);
        gl.glPopMatrix();

        gl.glPushMatrix();
            gl.glRotatef(angle_neptune, 0.0f, 1.0f, 0.0f);
            gl.glTranslatef(10.0f, 0.0f, 0.0f);
            // draw neptune
            planet[9].setScale(scalefactor[9]);
            planet[9].draw(gl);
        gl.glPopMatrix();

        if(rot_flag) {
            rot_sun -= 0.2f;
            angle_earth += orbital_earth;
            angle_mercury += orbital_mercury;
            angle_venus += orbital_venus;
            angle_mars += orbital_mars;
            angle_jupiter += orbital_jupiter;
            angle_saturn += orbital_saturn;
            angle_uranus += orbital_uranus;
            angle_neptune += orbital_neptune;

            if (angle_earth >= 360.0f) {
                angle_earth -= 360.0f;
            }
            if (angle_mercury >= 360.0f) {
                angle_mercury -= 360.0f;
            }
            if (angle_venus >= 360.0f) {
                angle_venus -= 360.0f;
            }
            if (angle_mars >= 360.0f) {
                angle_mars -= 360.0f;
            }
            if (angle_jupiter >= 360.0f) {
                angle_jupiter -= 360.0f;
            }
            if (angle_saturn >= 360.0f) {
                angle_saturn -= 360.0f;
            }
            if (angle_uranus >= 360.0f) {
                angle_uranus -= 360.0f;
            }
            if (angle_neptune >= 360.0f) {
                angle_neptune -= 360.0f;
            }
        }
        gl.glFlush();
    }
}
