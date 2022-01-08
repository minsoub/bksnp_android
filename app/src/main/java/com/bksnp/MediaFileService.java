package com.bksnp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MediaFileService {
    private String TAG = "MediaFileService";
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    private Context mContext;
    private String mFilePath;
    public MediaFileService(Context context) {
        mContext = context;
    }

    /** Create a file Uri for saving an image or video */
    public Uri getOutputMediaFileUri(int type){
        File file = getOutputMediaFile(type);
        mFilePath = file.getAbsolutePath();
        return FileProvider.getUriForFile(mContext, "com.bksnp.fileprovider", file);  // getOutputMediaFile(type));
        //return Uri.fromFile(getOutputMediaFile(type));
    }
    public String getPath() { return mFilePath; }

    /** Create a File for saving an image or video */
    public File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        //File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
        //        Environment.DIRECTORY_PICTURES), "OHNION");
        File mediaStorageDir = new File(mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "OHNION");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    public String getPathFromUri(Uri uri){
        Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null );
        cursor.moveToNext();
        String path = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA));
        //String path = cursor.getString( 0) ;  // cursor.getColumnIndex( 0 ) );
        cursor.close();

        return path;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public synchronized String base64ReadData(String filePath) throws IOException {
        byte[] b = getFileBinary(filePath);
        String base64data = Base64.getEncoder().encodeToString(b);

        return base64data;
    }
    /**
     * 파일을 바이너리 데이터로 읽어 들여서 리턴
     * @param filePath
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private synchronized byte[] getFileBinary(String filePath) throws IOException {
//        File file = new File(filePath);
//        byte[] data = new byte[(int) file.length()];
//        try (FileInputStream stream = new FileInputStream(file)) {
//            stream.read(data, 0, data.length);
//        }catch(Throwable e) {
//            e.printStackTrace();
//        }
//        return data;
          byte[] bytes = Files.readAllBytes(Paths.get(filePath));

          return bytes;
    }
}
