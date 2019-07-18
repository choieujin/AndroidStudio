package com.example.midtermapp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PickImageHelper {
    public static void selectImage(final Activity activity, String timeStamp){
        activity.startActivityForResult(getPickImageChooserIntent(activity,timeStamp), 9162);
    }

    public static Intent getPickImageChooserIntent(final Activity activity,String timeStamp) {
        Log.d("이미지","getPickImageChooserIntent");
        // Determine Uri of camera image to save.
        Uri outputFileUri = getCaptureImageOutputUri(activity,timeStamp);

        File file = new File(getRealPathFromURI(activity,outputFileUri));
        if (file.exists())
            file.delete();

        List<Intent> allIntents = new ArrayList<>();
        PackageManager packageManager = activity.getPackageManager();

        // collect all camera intents
        Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
        for (ResolveInfo res : listCam) {
            Intent intent = new Intent(captureIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(res.activityInfo.packageName);
            if (outputFileUri != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            }
            allIntents.add(intent);
        }

        // collect all gallery intents
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        List<ResolveInfo> listGallery = packageManager.queryIntentActivities(galleryIntent, 0);
        for (ResolveInfo res : listGallery) {
            Intent intent = new Intent(galleryIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(res.activityInfo.packageName);
            allIntents.add(intent);
        }

        // the main intent is the last in the list (fucking android) so pickup the useless one
        Intent mainIntent = allIntents.get(allIntents.size() - 1);
        for (Intent intent : allIntents) {
            if (intent.getComponent().getClassName().equals("com.android.documentsui.DocumentsActivity")) {
                mainIntent = intent;
                break;
            }
        }
        allIntents.remove(mainIntent);

        // Create a chooser from the main intent
        Intent chooserIntent = Intent.createChooser(mainIntent, "Obter imagem");

        // Add all other intents
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, allIntents.toArray(new Parcelable[allIntents.size()]));

        return chooserIntent;
    }


    /**
     * Get URI to image received from capture by camera.
     */
    private static Uri getCaptureImageOutputUri(Activity activity,String timeStamp) {
        Log.d("이미지","getCaptureImageOutputUri");
        Uri outputFileUri = null;
        Log.d("이미지", "getImage is not null");
        outputFileUri = Uri.fromFile(new File(
                Environment.getExternalStorageDirectory().getAbsolutePath(), timeStamp + ".jpeg"));
        Log.d("이미지", "helper : " + outputFileUri.getPath());
        return outputFileUri;
    }

    public static Uri getPickImageResultUri(Activity activity, Intent data, String timeStamp) {
        boolean isCamera = true;
        if (data != null) {
            String action = data.getAction();
            isCamera = action != null && action.equals(MediaStore.ACTION_IMAGE_CAPTURE);
        }
        return isCamera ? getCaptureImageOutputUri(activity,timeStamp) : data.getData();
    }

    public static String getRealPathFromURI(Activity activity,Uri contentUri) {
        Log.d("이미지","getRealPathFromURI");
        String result;
        Cursor cursor = activity.getContentResolver().query(contentUri, null, null, null, null);
        if (cursor == null) {
            result = contentUri.getPath();
            Log.d("이미지","getRealPathFromURI : "+result);
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }
}
