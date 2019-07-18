package com.example.myransac;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.imgproc.Imgproc;
import org.opencv.xfeatures2d.SIFT;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener {
    /* Log 앞에 붙일 태그 */
    String TAG = "SIFT_IMG_STITCHING";

    /* OpenCV Library 불러옴 */
    static {
        System.loadLibrary("opencv_java3");
    }

    /* 이동 및 확대에 사용할 Matrix형 변수들 */
    Matrix matrix = new Matrix();
    Matrix savedMatrix = new Matrix();

    /* 정지, 드래그, 확대 세 가지 상태로 나눔 */
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    int mode = NONE;

    /* 확대 시 사용할 변수들 */
    PointF start = new PointF();
    PointF mid = new PointF();
    float oldDist = 1f;

    /* 이미지뷰, Bitmap, Feature Detector, Descriptor, Keypoint들, 결과 값 행렬을 저장할 변수들 선언 */
    private ImageView imageView;
    private Bitmap inha_l, inha_r, plaza_l, plaza_r, mountain_l, mountain_r, rst_image, myl,myr;
    private SIFT sift = SIFT.create();

    private MatOfKeyPoint keyPoints1, keyPoints2;
    private Mat imgL, imgR, result;
    private List<DMatch> good_match;    // feature matching 결과를 저장할 변수

    /* Homography 행렬과 그 역행렬을 저장할 행렬 변수들  */
    private Mat P1_idx, P2_idx, homography, hompgraphy_inv;
    /* 실제 RANSAC을 통해 구해진 최종 Inlier 순번들을 저장할 행렬 */
    private ArrayList<Integer> inliers;

    /* Progress를 진행하고 있음을 보여주기 위한 dialog */
    private ProgressDialog p_dialog;

    /* 소요시간 측정을 위한 변수들 */
    private long nStart = 0, nEnd = 0;

    /* 진행중인 task 추적을 위한 변수 */
    private int progressTracker = 0;

    /* 여러가지 색상을 표현하기 위한 point들의 색상 값 */
    List<Scalar> point_color= new ArrayList<Scalar>(){{
        add(new Scalar(225, 0, 0)); add(new Scalar(0, 100, 0)); add(new Scalar(169, 169, 169)); add(new Scalar(0, 0, 200));
        add(new Scalar(255, 140, 0)); add(new Scalar(85, 107, 47)); add(new Scalar(30, 144, 255)); add(new Scalar(34, 139, 34));
        add(new Scalar(218, 165, 32)); add(new Scalar(173, 255, 47)); add(new Scalar(205, 92, 92)); add(new Scalar(75, 0, 130));
        add(new Scalar(152, 251, 150));
    }};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* 화면 항상 켜기 & 상태바, 액션바 제거 */
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        View decorView = getWindow().getDecorView();

        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        /* Layout 설정 및 ImageView 설정 */
        setContentView(R.layout.activity_main);
        imageView = this.findViewById(R.id.imageView);

        /* 크기를 조절할 수 있는 ImageView로 만듦 (왼쪽 상단으로 정렬) */
        imageView.setScaleType(ImageView.ScaleType.MATRIX);

        /* Bitmap 형식으로 사용할 영상들을 불러옴 */
        plaza_l = BitmapFactory.decodeResource(getResources(), R.drawable.plaza_l);
        plaza_r = BitmapFactory.decodeResource(getResources(), R.drawable.plaza_r);
        mountain_l= BitmapFactory.decodeResource(getResources(), R.drawable.mountain_l);
        mountain_r= BitmapFactory.decodeResource(getResources(), R.drawable.mountain_r);
        inha_l = BitmapFactory.decodeResource(getResources(), R.drawable.inha_l);
        inha_r = BitmapFactory.decodeResource(getResources(), R.drawable.inha_r);
        myl = BitmapFactory.decodeResource(getResources(), R.drawable.myl);
        myr = BitmapFactory.decodeResource(getResources(), R.drawable.myr);

        /* ImageView를 터치 동작에 반응하도록 함 */
        imageView.setOnTouchListener(this);
    }

    /* ProgressDialog를 비동기적으로 실행하기 위해 내부클래스 선언 */
    private class SIFT_tasks extends AsyncTask<Void, Void, Void> {
        /* 실행할 task를 지정해줄 변수 및 task의 이름 */
        int flag;
        String task_name;

        /* 생성자, 변수들 및 ProgressDialog 초기화 */
        public SIFT_tasks(int flag, String task_name) {
            this.flag = flag;
            this.task_name = task_name;

            p_dialog = new ProgressDialog(MainActivity.this);
            p_dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            p_dialog.setCancelable(false);
            p_dialog.setMessage(task_name);
        }

        /* 비동기화가 일어나기 전에 호출되는 함수, ProgressDialog를 출력 */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            p_dialog.show();
        }

        /* 비동기화가 일어나고 있는 과정에서 호출되는 함수 */
        @Override
        protected Void doInBackground(Void... arg0) {
            /* Task 번호에 따라 실행 */
            switch(flag) {
                case 1:
                    loadBitmap2MatImg();
                    break;
                case 2:
                    feature_detecting();
                    break;
                case 3:
                    feature_matching();
                    break;
                case 4:
                    ocv_warping();
                    break;
                case 5:
                    homography_RANSAC(P2_idx, P1_idx);  // 순서 주의!
                    forward_warping();
                    break;
                case 6:
                    homography_RANSAC(P2_idx, P1_idx);  // 순서 주의!
                    backward_warping();
                    break;
                default:
                    break;
            }

            return null;
        }

        /* 비동기화가 끝난 뒤에 호출되는 함수, ImageView를 업데이트한 뒤 ProgressDialog를 종료 */
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            imageView.setImageBitmap(rst_image);


            p_dialog.dismiss();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();

        /* Activity가 비활성화되기 전에 ProgressDialog를 종료 */
        if (p_dialog != null && p_dialog.isShowing()){
            p_dialog.dismiss();
            p_dialog = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        /* Activity가 비활성화되기 전에 ProgressDialog를 종료 */
        if (p_dialog != null && p_dialog.isShowing()){
            p_dialog.dismiss();
            p_dialog = null;
        }
    }

    /* 두 행렬을 이어주는 함수 */
    private Mat concatenate_img(Mat img1, Mat img2) {
        /* 받은 Mat형 변수들을 Size형으로 저장한 후 높이, 너비를 저장 */
        Size size1 = img1.size();
        int height1 = (int) size1.height;
        int width1 = (int) size1.width;

        Size size2 = img2.size();
        int height2 = (int) size2.height;
        int width2 = (int) size2.width;

        /* 두 영상을 합치는 것이므로 결과 영상의 높이는 두 영상 중 최대값을,
           너비는 두 영상의 너비의 합으로 설정 */
        int height_c = Math.max(height1, height2);
        int width_c = width1 + width2;

        /* 결과 영상 높이, 너비를 이용하여 생성해준 후 초기화 */
        Mat mResult = new Mat(height_c, width_c, CvType.CV_8UC4);
        mResult.setTo(new Scalar(0, 0, 0, 0));

        /* 두 영상을 결과 영상 행렬에 복사 */
        img1.copyTo(mResult.submat(0, height1, 0, width1));
        img2.copyTo(mResult.submat(0, height2, width1, width_c));

        return mResult;
    }

    /* Bitmap -> Mat으로 image load 함수 */
    private void loadBitmap2MatImg(){   // STEP 1
        /* Allocate memory for input & output Mat data */
        imgL = new Mat();
        imgR = new Mat();
        result = new Mat();

        /* Bitmap -> Mat */
        // TODO: input Bitmap 좌/우 영상이 들어가는 곳! --> 이곳을 수정하여 input image를 바꾼다!
        //Utils.bitmapToMat(plaza_l, imgL);
        //Utils.bitmapToMat(plaza_r, imgR);
        Utils.bitmapToMat(myl, imgL);
        Utils.bitmapToMat(myr, imgR);

        result = concatenate_img(imgL, imgR);   // 두 영상을 합친 영상을 return함
        rst_image = Bitmap.createBitmap(result.width(), result.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, rst_image);

        progressTracker = 1;    // load img finished.
    }

    private void drawKeyPoints(Mat output, MatOfKeyPoint keyPoints){
        KeyPoint kp[] =  keyPoints.toArray();
        for(int i=0; i<kp.length; i++){
            Scalar pColor = point_color.get(i % point_color.size());
            Imgproc.circle(output, new Point(kp[i].pt.x, kp[i].pt.y), (int)kp[i].size, pColor );
            Imgproc.line(output,
                    new Point(kp[i].pt.x, kp[i].pt.y),
                    new Point(kp[i].pt.x + kp[i].size * Math.cos(kp[i].angle),kp[i].pt.y + kp[i].size * Math.sin(kp[i].angle)),
                    pColor);
        }
    }

    /* 특징점들을 검출하여 출력해주는 함수 */
    private void feature_detecting() {      // STEP 2
        if(progressTracker < 1){
            Log.e(TAG, "Load img first");
            return;
        }
        /* 출력 영상 및 keypoint들을 저장할 Mat형 변수 */
        keyPoints1 = new MatOfKeyPoint();
        keyPoints2 = new MatOfKeyPoint();

        /* Feature detecting */
        Log.i(TAG, "Finding features");

        /* 시작 시간을 기록 */
        nStart = System.currentTimeMillis();

        sift.detect(imgL, keyPoints1);
        sift.detect(imgR, keyPoints2);

        /* 종료 시간을 기록한 뒤 그 시간을 Log로 출력 */
        nEnd = System.currentTimeMillis();
        Log.i(TAG, "Feature detecting computing time : " + (nEnd - nStart) + "ms");

        /* 찾은 keypoint들을 모두 그림 */
        Log.i(TAG, "Drawing features");
        Mat imgL_tmp = imgL.clone();
        Mat imgR_tmp = imgR.clone();
        drawKeyPoints(imgL_tmp, keyPoints1);
        drawKeyPoints(imgR_tmp, keyPoints2);

        /* 좌/우 영상을 합침 */
        result = concatenate_img(imgL_tmp, imgR_tmp);

        /* 결과 영상을 저장, Mat -> Bitmap */
        rst_image = Bitmap.createBitmap(result.width(), result.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, rst_image);
        progressTracker = 2;    // Feature detecting is finished.
    }

    /* 특징점들을 검출한 뒤 이를 matching하고 그 결과를 출력해주는 함수 */
    private void feature_matching() {       // STEP 3
        if(progressTracker < 2){
            Log.e(TAG, "Detecting features is first");
            return;
        }

        /* Feature descriptor를 저장할 Mat형 변수들 */
        Mat descriptor = new Mat();
        Mat descriptor2 = new Mat();

        /* 시작 시간을 기록 */
        nStart = System.currentTimeMillis();

        /* 미리 검출된 Keypoint들에 대한 기술자(descriptor)를 extractor를 이용하여 저장 */
        sift.compute(imgL, keyPoints1, descriptor);
        sift.compute(imgR, keyPoints2, descriptor2);

        /* Feature matching을 실행하여 첫 번째 영상에서의 keypoint 순번에
           두 번째 영상의 keypoint 순번을 연결 → mapping */
        /* matcher type :
         * FLANNBASED
         * BruteForce
         * BruteForce-L1
         * BruteForce-Hamming
         */
        Log.i(TAG, "Feature matching starts");
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);

        List<MatOfDMatch> matches = new ArrayList<MatOfDMatch>(){{}};
        matcher.knnMatch(descriptor, descriptor2, matches, 2);  // knn 의 k = 2 // 2개까지 후보 추출

        Log.i(TAG, "Descriptor's Dimension: " + descriptor.width());

        float ratio_thresh = .5f; // 1순위의 매칭 결과가 2순위 매칭 결과의 0.5배보다 더 가까운 값만을 취함
        MatOfDMatch good_matches = new MatOfDMatch();
        good_match = new ArrayList<DMatch>();

        for(int i=0; i<matches.size(); i++){
            DMatch[] knn_matches = matches.get(i).toArray();
            if(knn_matches[0].distance < ratio_thresh * knn_matches[1].distance)
                good_match.add(knn_matches[0]);
        }
        good_matches.fromList(good_match);  // Thresholding 을 통과한 후보만 match point로 저장

        /* 종료 시간을 기록한 뒤 그 시간을 Log로 출력 */
        nEnd = System.currentTimeMillis();
        Log.i(TAG, "Feature-matching computing time : " + (nEnd - nStart) + "ms");

        /* Matching 된 점들 사이에 선분들을 모두 그어줌 */
        Log.i(TAG, "Draw lines");

        result = concatenate_img(imgL, imgR); // concatenate img for drawing

        /* 각 keypoint들을 List 형태로 저장 */
        List<KeyPoint> pp1;
        pp1 = keyPoints1.toList();
        List<KeyPoint> pp2;
        pp2 = keyPoints2.toList();

        /* 만약 homogeneous 좌표들을 저장할 행렬이 비어있지 않으면 초기화 */
        if(P1_idx != null)
            P1_idx.release();
        if(P2_idx != null)
            P2_idx.release();

        /* 매치잉 잘된 key points 개수 X 3, double형의 채널이 1개인 행렬 생성 */
        P1_idx = new Mat(good_match.size(), 3, CvType.CV_64FC1);
        P2_idx = new Mat(good_match.size(), 3, CvType.CV_64FC1);

        // draw matches as Line
        for(int i=0; i<good_match.size(); i++){
            double img1_x = pp1.get(good_match.get(i).queryIdx).pt.x;
            double img1_y = pp1.get(good_match.get(i).queryIdx).pt.y;
            double img2_x = pp2.get(good_match.get(i).trainIdx).pt.x + imgL.size().width;
            double img2_y = pp2.get(good_match.get(i).trainIdx).pt.y;
            /* draw part */
            Imgproc.line(result, new Point(img1_x, img1_y), new Point(img2_x, img2_y), point_color.get(i % point_color.size()));

            /* save Good matched - KeyPoint as homogeneous coordinates */
            P1_idx.put(i, 0, (int) img1_x);
            P1_idx.put(i, 1, (int) img1_y);
            P1_idx.put(i, 2, 1);

            P2_idx.put(i, 0, (int)img2_x - imgL.size().width);
            P2_idx.put(i, 1, (int) img2_y);
            P2_idx.put(i, 2, 1);
        }

        /* 결과 영상을 저장, Mat -> Bitmap */
        rst_image = Bitmap.createBitmap(result.width(), result.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, rst_image);
        progressTracker = 3;    // Feature Matching is finished.
    }

    /* OPENCV warping */
    private void ocv_warping(){  // STEP 4
        if(progressTracker < 3){
            Log.e(TAG, "Matching features is first");
            return;
        }

        /* 시작 시간을 기록 */
        nStart = System.currentTimeMillis();

        /* MatOfPoint2f 자료 형으로 변환하기 위한 List형 자료형 */
        List<Point> img1List = new ArrayList<Point>(); // image left
        List<Point> img2List = new ArrayList<Point>(); // image right

        List<KeyPoint> keypointsList_of_leftImg = keyPoints1.toList();
        List<KeyPoint> keypointsList_of_rightImg = keyPoints2.toList();

        //putting the points of the good matches into above structures
        for(int i = 0; i<good_match.size(); i++){
            img1List.add(keypointsList_of_leftImg.get(good_match.get(i).queryIdx).pt);
            img2List.add(keypointsList_of_rightImg.get(good_match.get(i).trainIdx).pt);
        }

        /* 좌/우 영상의 good_match 좌표를 MatOfPoint2f 자료 형으로 변환 */
        MatOfPoint2f imgL_P2 = new MatOfPoint2f();
        imgL_P2.fromList(img1List);
        MatOfPoint2f imgR_P2 = new MatOfPoint2f();
        imgR_P2.fromList(img2List);

        /* 호모그래피 행렬 찾기 img2 -> img1 */
        Mat H = Calib3d.findHomography(imgR_P2, imgL_P2, Calib3d.RANSAC, 3);

        /* warping */
        result = new Mat();
        Imgproc.warpPerspective(imgR, result, H, new Size(imgL.width()+imgR.width(), imgR.height()));
        imgL.copyTo(result.submat(0, imgL.height(),0, imgL.width()));

        /* 종료 시간을 기록한 뒤 그 시간을 Log로 출력 */
        nEnd = System.currentTimeMillis();
        Log.i(TAG, "OpenCV Library warping computing time : " + (nEnd - nStart) + "ms");

        /* 결과 영상을 저장, Mat -> Bitmap */
        rst_image = Bitmap.createBitmap(result.width(), result.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, rst_image);
        progressTracker = 4;
    }


    /* 임의로 N개의 점을 받은 최대값, 최소값 사이에서 만들어 받은 ArrayList에 저장하는 함수 */
    public void random_selection(ArrayList<Integer> idx, int N, int min, int max) {
        /* 임시로 임의 값을 받을 변수, 임의 값의 개수를 받을 변수,
           임의로 만든 값이 해당 배열에 있는지 그 여부를 저장할 변수 선언 */
        int temp, count = 0, check;

        /* 받은 배열 초기화 */
        idx.clear();

        /* 만든 임의 값이 입력 받은 만들어주고자 하는 임의 값의 개수와 같아질 때까지 실행 */
        while (count < N) {
            temp = (int) ((Math.random() * (max - min))) + min;

            /* 임의로 만든 값이 해당 배열에 있는지 확인 */
            check = idx.indexOf(temp);

            /* 만약 없다면 추가할 수 있으므로 추가하고 개수 1 증가 */
            if (check < 0) {
                idx.add(temp);
                count += 1;
            }
        }
    }

    /* Homography 행렬을 계산하는 함수 */
    public void homography_RANSAC(Mat P1_idx, Mat P2_idx) {  // STEP 3.5
        if(progressTracker < 3){
            Log.e(TAG, "Matching features is first");
            return;
        }

        // 이미 호모그래피를 구한 경우 pass
        if(homography!=null) return;

        /* 시작 시간을 기록 */
        nStart = System.currentTimeMillis();

        /* Euclidean distance threshold, inlier percentage thresholds,
           현재까지의 최고 inlier percentage, inlier 개수를 저장할 변수들 선언 */
        double euclidean_thresh = 0.4;
        double percentage_thresh = 0.6;
        double best_percentage_ever = -1;
        double inlier_count;

        /* Inlier들 증 임의로 선택된 점들 및 추정된 inlier들을 저장할 배열 */
        ArrayList<Integer> random_idx = new ArrayList<>();
        ArrayList<Integer> estimated_inliers = new ArrayList<>();

        /* A * H = B의 형태에서 A, B를 선언 후 0으로 초기화 */
        Mat A = new Mat(8, 8, CvType.CV_64FC1);
        Mat B = new Mat(8, 1, CvType.CV_64FC1);
        A.setTo(new Scalar(0));
        B.setTo(new Scalar(0));

        /* 추정된 homography 임시 행렬을 저장할 Mat형 변수 선언 */
        Mat homo_temp = new Mat();

        /* 최종 homography 행렬을 저장할 Mat형 변수 초기화  */
        homography = new Mat();

        /* P1.idx에 추정한 homography 행렬을 곱한 값과 P2.idx와의 거리를 저장할 Mat형 변수 */
        Mat dist = new Mat(1, P1_idx.rows(), CvType.CV_64FC1);

        /* A * H = B에서 각 점은 A, B에서의 두 행을 만들어주므로
           그 두 행을 저장할 임시 Mat형 변수 선언 및 초기화 (H: homography matrix) */

        /*           ┌                                 ┐
                     │ A11 A12 A13 A14 A15 A16 A17 A18 │
           A_slice = │ A21 A22 A23 A24 A25 A26 A27 A28 │
                     └                                 ┘ ,
                     ┌     ┐
                     │ B11 │
           B_slice = │ B21 │
                     └     ┘ */
        Mat A_slice = new Mat(2, 8, CvType.CV_64FC1);
        Mat B_slice = new Mat(2, 1, CvType.CV_64FC1);
        A_slice.setTo(new Scalar(0));

        /* 첫 번째 영상에서의 inlier에 homography 행렬을 곱한 값을 저장할 행렬 */
        Mat homo_idx = new Mat();

        /* 임시 변수들 선언 */
        int idx, x1, x2, y1, y2;

        /* 최대 반복 수 지정 */
        int iteration = 200;

        /* 지정한 반복 수 만큼 for문 반복 */
        for (int i = 0; i < iteration; i++) {
            /* 0 ~ (inlier 개수 - 1) 사이에서 4개의 임의 정수를 생성하여 배열에 저장 */
            random_selection(random_idx, 4, 0, P1_idx.rows() - 1);

            /* 4개의 점을 가져와 A * H = B에서 A, B를 완성 */
            for (int j = 0; j < 4; j++)
            {
                /* 한 점의 순번을 가져옴 */
                idx = random_idx.get(j);

                /* TODO: A_slice, B_slice 행렬 완성, A와 B에서 각각 두 행씩 가져옴 */
                /* 사용 함수: row(idx).get, put, row(idx).copyTo, submat */
                /* 두 영상에서의 inlier에서 해당 순번의 x, y값들을 가져옴 */
                x1 = (int) P1_idx.row(idx).get(0, 0)[0];
                y1 = (int) P1_idx.row(idx).get(0, 1)[0];
                x2 = (int) P2_idx.row(idx).get(0, 0)[0];
                y2 = (int) P2_idx.row(idx).get(0, 1)[0];

                /* A11, A12, A13, A24, A25, A26을 x1 y1 1로 변경 */
                P1_idx.row(idx).copyTo(A_slice.submat(0, 1, 0, 3));
                P1_idx.row(idx).copyTo(A_slice.submat(1, 2, 3, 6));

                /* A17, A18, A27, A28에 값 변경 후 A의 해당 칸에 넣음 */
                A_slice.put(0, 6, -x1 * x2);
                A_slice.put(0, 7, -y1 * x2);
                A_slice.put(1, 6, -x1*y2);
                A_slice.put(1, 7, -y1*y2);
                A_slice.copyTo(A.submat(2 * j, 2 * j + 2, 0, 8));

                /* B11, B21 값을 바꾼 후 B의 해당 칸에 넣음 */
                B_slice.put(0, 0, x2);
                B_slice.put(1, 0, y2);
                B_slice.copyTo(B.submat(2 * j, 2 * j + 2, 0, 1));
            }

            /* H를 Singular Value Decomposition 방법(특이 값 분해)을 이용하여 구함 */
            Core.solve(A, B, homo_temp, Core.DECOMP_SVD);

            /* H의 마지막 값에 1을 추가 */
            homo_temp.push_back(new Mat(1, 1, CvType.CV_64FC1).setTo(new Scalar(1)));

            /* homo_temp을 그 크기로 나눠준 후(단위 벡터) 1채널 3행 짜리로 다시 변경시켜 3 X 3 행렬로 변경 */
            Core.divide(homo_temp, new Scalar(Core.norm(homo_temp)), homo_temp);
            homo_temp = homo_temp.reshape(1, 3);

            /* homo_idx = homo_temp * P1_idx.t() → P2_idx의 값과 비교해주기 위함 */
            Core.gemm(homo_temp, P1_idx.t(), 1, new Mat(), 0, homo_idx);

            /* Homogeneous 특성을 이용하여 세 번째 행의 값들을 모든 행에 나누어 줌 */
            Core.divide(homo_idx.row(0), homo_idx.row(2), homo_idx.row(0));
            Core.divide(homo_idx.row(1), homo_idx.row(2), homo_idx.row(1));
            Core.divide(homo_idx.row(2), homo_idx.row(2), homo_idx.row(2));

            /* 그 차이를 낸 후 각 원소에 제곱을 하고 모든 열에서의 각각 원소를
               다 더해 한 행로 만들어 준 다음 그 원소 값들에 제곱근을 씌움 → Euclidean distance */
            Core.subtract(homo_idx, P2_idx.t(), homo_idx);
            Core.pow(homo_idx, 2, homo_idx);
            Core.reduce(homo_idx, dist, 0, Core.REDUCE_SUM);
            Core.sqrt(dist, dist);

            /* Inlier 개수 및 추정된 inlier들을 저장할 행렬을 초기화 */
            inlier_count = 0;
            estimated_inliers.clear();

            /* 위에서 구한 모든 거리 값 중 Euclidean distance threshold보다
               작은 값이면 inlier 개수를 1 증가시키고 그 순번을 해당 행렬에 저장 */
            for (int k = 0; k < dist.cols(); k++) {
                if (dist.get(0, k)[0] < euclidean_thresh) {
                    inlier_count += 1;
                    estimated_inliers.add(k);
                }
            }


            /* 그 개수의 percentage가 percentage threshold보다 크면 그 추정한 inlier들을
               실제로 사용할 Inlier 행렬에 넣고 for문을 빠져나옴 */
            if (inlier_count / (double) dist.cols() > percentage_thresh) {
                inliers = (ArrayList<Integer>) estimated_inliers.clone();
                break;
            }

            /* 그 percentage가 threshold보다 작거나 같고 현재까지의 최고 percentage보다 크면
                그 추정한 inlier들을 실제로 사용할 inlier에 넣고 그 최고 값을 갱신 */
            if (inlier_count / (double) dist.cols() > best_percentage_ever) {
                inliers = (ArrayList<Integer>) estimated_inliers.clone();
                best_percentage_ever = inlier_count / (double) dist.cols();
            }
        }


        /* A, B Memory 해제 */
        A.release();
        B.release();

        /* 다시 Inlier의 개수에 맞게 A, B를 생성 */
        A = new Mat(inliers.size() * 2, 8, CvType.CV_64FC1);
        B = new Mat(inliers.size() * 2, 1, CvType.CV_64FC1);

        /* A, B를 완성시킴 */
        for (int j = 0; j < inliers.size(); j++) {
            idx = inliers.get(j);

            /* TODO: A_slice, B_slice 행렬 완성, 위 과정과 동일 */
            /* 사용 함수: row(idx).get, put, row(idx).copyTo, submat */
            /* 두 영상에서의 inlier에서 해당 순번의 x, y값들을 가져옴 */
            x1 = (int) P1_idx.row(idx).get(0, 0)[0];
            y1 = (int) P1_idx.row(idx).get(0, 1)[0];
            x2 = (int) P2_idx.row(idx).get(0, 0)[0];
            y2 = (int) P2_idx.row(idx).get(0, 1)[0];

            /* A11, A12, A13, A24, A25, A26을 x1 y1 1로 변경 */
            P1_idx.row(idx).copyTo(A_slice.submat(0, 1, 0, 3));
            P1_idx.row(idx).copyTo(A_slice.submat(1, 2, 3, 6));

            /* A17, A18, A27, A28에 값 변경 후 A의 해당 칸에 넣음 */
            A_slice.put(0, 6, -x1 * x2);
            A_slice.put(0, 7, -y1 * x2);
            A_slice.put(1, 6, -x1*y2);
            A_slice.put(1, 7, -y1*y2);
            A_slice.copyTo(A.submat(2 * j, 2 * j + 2, 0, 8));

            /* B11, B21 값을 바꾼 후 B의 해당 칸에 넣음 */
            B_slice.put(0, 0, x2);
            B_slice.put(1, 0, y2);
            B_slice.copyTo(B.submat(2 * j, 2 * j + 2, 0, 1));
        }

        /* H를 구함 */
        Core.solve(A, B, homography, Core.DECOMP_SVD);

        /* 1을 마지막에 넣고 그 크기로 모두 나눠준 후 3 X 3 행렬로 만들어 줌 (homogeneous) */
        homography.push_back(new Mat(1, 1, CvType.CV_64FC1).setTo(new Scalar(1)));
        Core.divide(homography, new Scalar(Core.norm(homography)), homography);
        homography = homography.reshape(1, 3);

        /* 종료 시간을 기록한 뒤 그 시간을 Log로 출력 */
        nEnd = System.currentTimeMillis();
        Log.i(TAG, "Homography computing time : " + (nEnd - nStart) + "ms");

        /* Memory 해제 */
        dist.release();
        estimated_inliers.removeAll(estimated_inliers);
        random_idx.removeAll(random_idx);
        homo_idx.release();
        A_slice.release();
        B_slice.release();
    }

    /* forward warping */
    private void forward_warping(){  // STEP 4
        if(progressTracker < 3){
            Log.e(TAG, "Matching features and finding homography are first");
            return;
        }

        /* 시작 시간을 기록 */
        nStart = System.currentTimeMillis();

        int height1 = imgL.height();
        int width1 = imgL.width();
        int height2 = imgR.height();
        int width2 = imgR.width();

        /* 두 번째 영상(warping할 영상)의 높이와 너비를 이용하여 x, y, z index들을 저장할 행렬들 생성 */
        Mat idx_x = new Mat(height2, width2, CvType.CV_64FC1);
        Mat idx_y = new Mat(height2, width2, CvType.CV_64FC1);
        Mat idx_z = new Mat(1, height2 * width2, CvType.CV_64FC1);

        /* x index 행렬의 모든 원소를 해당 열의 번호로 초기화 */
        for (int c = 0; c < width2; c++) {
            idx_x.col(c).setTo(new Scalar(c));
        }

        /* y index 행렬의 모든 원소를 해당 행의 번호로 초기화 */
        for (int r = 0; r < height2; r++) {
            idx_y.row(r).setTo(new Scalar(r));
        }

        /* z index 모든 원소를 1로 초기화 */
        idx_z.row(0).setTo(new Scalar(1));

        /* x, y index를 저장하는 행렬을 1채널, (두 번째 영상의 높이 * 너비) X 1 행렬로 변형 */
        idx_x = idx_x.reshape(1, height2 * width2);
        idx_y = idx_y.reshape(1, height2 * width2);

        /* Index 행렬 완성 → 두 번째 영상으로부터 나올 수 있는 모든 좌표들 */
        Mat inv_idx = new Mat();
        inv_idx.push_back(idx_x.t());
        inv_idx.push_back(idx_y.t());
        inv_idx.push_back(idx_z);

        /* inv_idx = homography * inv_idx → Warp 됐을 때의 모든 좌표들 저장 */
        Core.gemm(homography, inv_idx, 1, new Mat(), 0, inv_idx);

        /* 세 번째 행의 값들로 다른 행들을 모두 나눠줌 */
        Core.divide(inv_idx.row(0), inv_idx.row(2), inv_idx.row(0));
        Core.divide(inv_idx.row(1), inv_idx.row(2), inv_idx.row(1));

        /* 첫 번째(x 좌표), 두 번째(y 좌표) 행에서의 최대, 최소 값을 저장 → 두 번째 영상 크기 */
        Core.MinMaxLocResult mmlr = Core.minMaxLoc(inv_idx.row(0));
        Core.MinMaxLocResult mmlr2 = Core.minMaxLoc(inv_idx.row(1));

        /* 만들 영상의 최대, 최소 좌표를 저장 */
        int col_max = (int) mmlr.maxVal;
        int col_min = (int) mmlr.minVal;
        int row_max = (int) mmlr2.maxVal;
        int row_min = (int) mmlr2.minVal;

        /* 최소 좌표와 0을 비교하여 더 작은 값을 이용하여 높이, 너비를 구하는데 사용,
           row_min, col_min 값이 음수로 나올 수 있음 */
        int min_r = Math.min(0, row_min);
        int min_c = Math.min(0, col_min);

        /* 결과 영상의 높이, 너비를 구함 */
        int height = Math.max(row_max, height1) + Math.abs(min_r);
        int width = Math.max(col_max, width1) + Math.abs(min_c);

        /* 각 점의 좌표를 받을 변수들 */
        int x, y;

        /* 만약 결과 영상 행렬이 비어있지 않으면 Memory 해제 */
        if (result != null) {
            result.release();
        }

        /* 결과 영상 행렬을 위에서 계산한 높이, 너비로 생성 및 초기화 */
       result = new Mat(height, width, CvType.CV_8UC4, Scalar.all(0));

        /* 첫 번째 영상은 그대로 복사 */
        imgL.copyTo(result.submat(Math.abs(min_r), Math.abs(min_r) + height1, Math.abs(min_c), Math.abs(min_c) + width1));

        /* 각 좌표 값에 맞게 색상 값을 가져와서 채움 */
        for (int r = 0; r < height2; r++) {
            for (int c = 0; c < width2; c++) {
                /* 각 좌표를 계산 후 결과 영상에 넣음 → 정수 좌표, 구멍이 생김 */
                x = (int) inv_idx.get(0, r * width1 + c)[0] + Math.abs(min_c);
                y = (int) inv_idx.get(1, r * width1 + c)[0] + Math.abs(min_r);

                result.put(y, x, imgR.get(r, c));
            }
        }

        /* 종료 시간을 기록한 뒤 그 시간을 Log로 출력 */
        nEnd = System.currentTimeMillis();
        Log.i(TAG, "Forward warping computing time : " + (nEnd - nStart) + "ms");

        /* 결과 영상을 저장, Mat -> Bitmap */
        rst_image = Bitmap.createBitmap(result.width(), result.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, rst_image);
        progressTracker = 4;
    }

    /* backward warping */
    private void backward_warping(){  // STEP 4
        if(progressTracker < 3){
            Log.e(TAG, "Matching features and finding homography are first");
            return;
        }

        /* 시작 시간을 기록 */
        nStart = System.currentTimeMillis();

        /* Forward warping과 같은 과정 실행 */
        int height1 = imgL.height();
        int width1 = imgL.width();
        int height2 = imgR.height();
        int width2 = imgR.width();

        Mat idx_x = new Mat(height2, width2, CvType.CV_64FC1);
        Mat idx_y = new Mat(height2, width2, CvType.CV_64FC1);
        Mat idx_z = new Mat(1, height2 * width2, CvType.CV_64FC1);

        for (int c = 0; c < width2; c++) {
            idx_x.col(c).setTo(new Scalar(c));
        }

        for (int r = 0; r < height2; r++) {
            idx_y.row(r).setTo(new Scalar(r));
        }

        /* z index 모든 원소를 1로 초기화 */
        idx_z.row(0).setTo(new Scalar(1));

        idx_x = idx_x.reshape(1, height2 * width2);
        idx_y = idx_y.reshape(1, height2 * width2);

        Mat inv_idx = new Mat();
        inv_idx.push_back(idx_x.t());
        inv_idx.push_back(idx_y.t());
        inv_idx.push_back(idx_z);

        Core.gemm(homography, inv_idx, 1, new Mat(), 0, inv_idx);

        Core.divide(inv_idx.row(0), inv_idx.row(2), inv_idx.row(0));
        Core.divide(inv_idx.row(1), inv_idx.row(2), inv_idx.row(1));

        Core.MinMaxLocResult mmlr = Core.minMaxLoc(inv_idx.row(0));
        Core.MinMaxLocResult mmlr2 = Core.minMaxLoc(inv_idx.row(1));

        int col_max = (int) mmlr.maxVal;
        int col_min = (int) mmlr.minVal;
        int row_max = (int) mmlr2.maxVal;
        int row_min = (int) mmlr2.minVal;

        int min_r = Math.abs(Math.min(0, row_min));
        int min_c = Math.abs(Math.min(0, col_min));

        int height = Math.max(row_max, height1) + Math.abs(min_r);
        int width = Math.max(col_max, width1) + Math.abs(min_c);

        /* --------------------------- 여기부터 달라짐 --------------------------- */

        /* Warping 된 영상의 높이, 너비 */
        int wp_height = row_max - row_min;
        int wp_width = col_max - col_min;

        /* Warping 된 영상의 모든 좌표들을 저장할 행렬들 */
        Mat idx_x2 = new Mat(wp_height, wp_width, CvType.CV_64FC1);
        Mat idx_y2 = new Mat(wp_height, wp_width, CvType.CV_64FC1);
        Mat idx_z2 = new Mat(1, wp_height * wp_width, CvType.CV_64FC1);

        /* TODO: 위와 같은 방법으로 for문을 통해 warp 된 영상에서의 모든 점 좌표를 가져옴 */
        /* x index 행렬에 warping 된 영상에서 나올 수 있는 x 좌표를 모두 저장 */
        // Fill up code - homework
        Log.d("행렬",""+wp_height+","+wp_width);
        Log.d("행렬 : ",""+idx_x2.size());
        Log.d("행렬 : col_max",""+col_max);
        for (int c = col_min; c < col_max; c++) {
            idx_x2.col(c-col_min).setTo(new Scalar(c));
        }

        /* y index 행렬에 Warping 된 영상에서 나올 수 있는 y 좌표를 모두 저장 */
        // Fill up code - homework
        for (int r = row_min; r < row_max; r++) {
            idx_y2.row(r-row_min).setTo(new Scalar(r));
        }

        /* z index 모든 원소를 1로 초기화 */
        // Fill up code - homework
        idx_z2.row(0).setTo(new Scalar(1));

        /* x, y index를 저장하는 행렬을 1채널, (warping 된 영상의 높이 * 너비) X 1 행렬로 변형 */
        // Fill up code - homework
        idx_x2 = idx_x2.reshape(1, wp_height*wp_width);
        idx_y2 = idx_y2.reshape(1, wp_height*wp_width);

        /* Index 행렬 완성 */
        Mat inv_idx2 = new Mat();
        inv_idx2.push_back(idx_x2.t());
        inv_idx2.push_back(idx_y2.t());
        inv_idx2.push_back(idx_z2);

        /* Homography 행렬의 역행렬을 구함 */
        hompgraphy_inv = homography.inv();

        /* inv_idx2 = hompgraphy_inv * inv_idx2 → 반대로 warping 됐을 때의 좌표들 저장 */
        Core.gemm(hompgraphy_inv, inv_idx2, 1, new Mat(), 0, inv_idx2);

        /* 세 번째 행의 값들로 다른 행들을 모두 나눠줌 */
        Core.divide(inv_idx2.row(0), inv_idx2.row(2), inv_idx2.row(0));
        Core.divide(inv_idx2.row(1), inv_idx2.row(2), inv_idx2.row(1));

        /* 각 점의 좌표 및 소수점 아래 값들을 받을 변수들 */
        double x, y, delta_x, delta_y;

        /* Interpolation 시 사용할 배열, R, G, B, alpha 값을 나타내며 alpha 값은 항상 255 */
        double[] b_inter = new double[4];
        b_inter[3] = 255;

        /* 만약 결과 영상 행렬이 비어있지 않으면 Memory 해제 */
        if (result != null) {
            result.release();
        }

        /* 결과 영상 행렬을 위에서 계산한 높이, 너비로 생성 */
        result = new Mat(height, width, CvType.CV_8UC4, Scalar.all(0));
        result.setTo(new Scalar(0));

        /* 첫 번째 영상은 그대로 복사 */
        imgL.copyTo(result.submat(min_r, min_r + height1, min_c, min_c + width1));

        /* Bilinear interpolation을 이용하여 빈 부분을 채움 */
        for (int r = 0; r < wp_height; r++) {
            for (int c = 0; c < wp_width; c++) {
                /* 반대로 Warping 됐을 때의 좌표들이 저장된 행렬에서 해당 좌표를 들고옴 */
                //사다리꼴좌표 -> img2의 좌표
                x = inv_idx2.get(0, r * wp_width + c)[0];
                y = inv_idx2.get(1, r * wp_width + c)[0];
                /* 만약 현재 가져온 좌표들이 두 번째 영상의 높이, 너비 내에 있으면 실행 */
                if ( (x < width2 - 1) && (y < height2 - 1) && (x > 1) && (y > 1)) {
                    /* 소수점 아래 부분을 가져옴 → 1과 0 사이에 얼마나 떨어져있는지 저장 */
                    delta_x = x - (int) (x);
                    delta_y = y - (int) (y);
                    //Log.d("좌표 : ","x="+x+", y="+y);
                    /* TODO: Bilinear interpolation 공식을 이용하여 색상 값을 계산 */
                    /* R, G, B에 대해 Bilinear interpolation을 해주기 위해 for문 사용 */
                    for (int i = 0; i < 3; i++) {
                        int x1 = (int)Math.floor(x);
                        int y1 = (int)Math.floor(y);
                        int x2 = (int)Math.ceil(x);
                        int y2 = (int)Math.ceil(y);
                        b_inter[i] = (x2 - x)*(y2 - y)*imgR.get(y1, x1)[i] +
                                (x2 - x)*(y - y1)*imgR.get(y2, x1)[i] +
                                (x - x1)*(y2 - y)*imgR.get(y1, x2)[i] +
                                (x - x1)*(y - y1)*imgR.get(y2, x2)[i];
                        /* x2 - x1 = y2 - y1 = 1,
                           x - x1 = delta_x, y - y1 = delta_y,l
                           x2 - x = 1 - delta_x, y2 - y = y - delta_y
                           f(Q11) = 두 번째 영상에서의 (x1, y1)에서 색상 값        imgR.get(r, c)
                           f(Q12) = 두 번째 영상에서의 (x1, y2)에서 색상 값
                           f(Q21) = 두 번째 영상에서의 (x2, y1)에서 색상 값
                           f(Q22) = 두 번째 영상에서의 (x2, y2)에서 색상 값
                           b_inter[i] = (x2 - x)(y2 - y)f(Q11)[i] + (x2 - x)(y - y1)f(Q12)[i]
                                        + (x - x1)(y2 - y)f(Q21)[i] + (x - x1)(y - y1)f(Q22)[i]
                           1 / (x2 - x1)(y2 - y1) = 1이므로 생략 */
                    }

                    /* 결과 영상에 좌표 및 색깔 값 저장 */
                    result.put(r + row_min + min_r, c + col_min + min_c, b_inter);
                }
            }
        }

        /* 종료 시간을 기록한 뒤 그 시간을 Log로 출력 */
        nEnd = System.currentTimeMillis();
        Log.i(TAG, "Backward warping computing time : " + (nEnd - nStart) + "ms");

        /* Memory 해제 */
        idx_x.release();
        idx_y.release();
        idx_z.release();
        idx_x2.release();
        idx_y2.release();
        idx_z2.release();

        /* 결과 영상을 저장, Mat -> Bitmap */
        rst_image = Bitmap.createBitmap(result.width(), result.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, rst_image);
        progressTracker = 4;
    }
//////////////////////////////////////////////////TOUCH EVENT 분리선///////////////////////////////////////////////////////////////
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        /* 크기 저장 */
        float scale;

        /* 터치에 관한 이벤트들 처리 */
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            /* 첫 손가락 하나만 터치했을 때 */
            case MotionEvent.ACTION_DOWN:
                /* 현재 행렬을 저장하고 드래그 모드로 전환 */
                savedMatrix.set(matrix);
                start.set(event.getX(), event.getY());
                Log.d(TAG, "mode: DRAG");
                mode = DRAG;
                break;
            /* 첫 손가락을 떼었을 때 */
            case MotionEvent.ACTION_UP:
                break;
            /* 두 번째 손가락을 떼었을 때 */
            case MotionEvent.ACTION_POINTER_UP:
                Log.d(TAG, "mode: NONE");
                mode = NONE;
                break;
            /* 두 번째 손가락도 터치했을 때 */
            case MotionEvent.ACTION_POINTER_DOWN:
                /* 두 손가락이 터치한 지점 사이의 거리 측정 */
                oldDist = spacing(event);

                /* 최소 거리보다 클 경우 두 터치 지점의 중간 지점을 저장한 후
                   확대, 축소 모드로 전환 */
                if (oldDist > 5f) {
                    savedMatrix.set(matrix);
                    midPoint(mid, event);
                    Log.d(TAG, "mode: ZOOM");
                    mode = ZOOM;
                }
                break;
            /* 터치한 손가락이 움직일 때 */
            case MotionEvent.ACTION_MOVE:
                /* 모드에 따라 드래그 또는 확대, 축소 실행 */
                if (mode == DRAG) {
                    matrix.set(savedMatrix);

                    matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                }
                else if (mode == ZOOM) {
                    float newDist = spacing(event);

                    if (newDist > 5f) {
                        matrix.set(savedMatrix);

                        scale = newDist / oldDist;

                        matrix.postScale(scale, scale, mid.x, mid.y);
                    }
                }
                break;
        }

        /* 바뀐 값 적용 */
        imageView.setImageMatrix(matrix);

        /* 이벤트가 성공적으로 처리되었음을 알림 */
        return true;
    }

    /* 두 점의 거리 측정 */
    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /* 두 점의 중간 점 획득 */
    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }
//////////////////////////////////////////////////BUTTON CLICK EVENT 분리선///////////////////////////////////////////////////////////////
    /* Load images 버튼을 눌렀을 때 호출 */
    public void load_img_click(View v) {
        /* 비동기화를 위해 클래스 변수 선언 및 실행 */
        SIFT_tasks load_img = new SIFT_tasks(1, "Load left and right images");
        load_img.execute();
    }

    /* Feature detecting 버튼을 눌렀을 때 호출 */
    public void feature_detecting_click(View v) {
        /* 비동기화를 위해 클래스 변수 선언 및 실행 */
        SIFT_tasks feature_detecting = new SIFT_tasks(2, "Feature detecting");
        feature_detecting.execute();
    }

    /* Feature matching 버튼을 눌렀을 때 호출 */
    public void feature_matching_click(View v) {
        /* 비동기화를 위해 클래스 변수 선언 및 실행 */
        SIFT_tasks feature_detecting = new SIFT_tasks(3, "Feature matching");
        feature_detecting.execute();
    }

    /* ocv warping 버튼을 눌렀을 때 호출 */
    public void ocv_warping_click(View v) {
        /* 비동기화를 위해 클래스 변수 선언 및 실행 */
        SIFT_tasks ocv_warping = new SIFT_tasks(4, "OpenCV warping");
        ocv_warping.execute();
    }

    /* Forward warping 버튼을 눌렀을 때 호출 */
    public void forward_warping_click(View v) {
        /* 비동기화를 위해 클래스 변수 선언 및 실행 */
        SIFT_tasks forward_warping = new SIFT_tasks(5, "Forward warping");
        forward_warping.execute();
    }

    /* Forward warping 버튼을 눌렀을 때 호출 */
    public void backward_warping_click(View v) {
        /* 비동기화를 위해 클래스 변수 선언 및 실행 */
        SIFT_tasks backward_warping = new SIFT_tasks(6, "Backward warping");
        backward_warping.execute();
    }
}