package com.bksnp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;
import com.google.firebase.dynamiclinks.ShortDynamicLink;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;
import java.util.Scanner;

/**
 * MainActivity Class
 */
public class MainActivity extends Activity {
    private WebView mWebView;    // WebView define
    private WebSettings mWebSettings;   // WebView setting
    private final Handler mHandler = new Handler();

    private Context mContext;
    private DatabaseReference mRef;
    private MediaFileService mService;
    private String TAG = "BKSNP";
    private String mToken = "";
    private Uri mFileUri;
    private String mFilePath;
    private ValueCallback mFilePathCallback;
    private ValueCallback<Uri> mUploadMessage;
    private String mCameraPhotoPath;
    private Uri mCapturedImageURI;
    private String mDirname;
    private String mDeepLinkUrl;
    private String mMessage;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final int INPUT_FILE_REQUEST_CODE = 701;
    private static final int FILECHOOSER_RESULTCODE = 702;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            Log.d("receiver", "Got message: " + message);
            mWebView.loadUrl("javascript:getNotification('" + mMessage + "')");
            mMessage = message;
        }
    };
    //@SuppressLint("JavascriptInterface")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDeepLinkUrl = null;
        mMessage = null;
        // Token 가져오기
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                            return;
                        }

                        // Get new FCM registration token
                        String token = task.getResult();
                        mToken = token;

                        // Log and toast
                        String msg = getString(R.string.msg_token_fmt, token);
                        Log.d(TAG, msg);
                        //Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });

        verifyStoragePermissions(this);

        // WebView Start
        mWebView = (WebView) findViewById(R.id.webView);

        mWebView.setWebViewClient(new WebViewClient());  // 클릭시 새창 안뜨게

        mWebSettings = mWebView.getSettings();           // 세부 세팅 등록
        mWebSettings.setJavaScriptEnabled(true);         // 자바스크립트 허용
        mWebSettings.setSupportMultipleWindows(true);   // 새창 띄우기 허용 여부
        mWebSettings.setJavaScriptCanOpenWindowsAutomatically(true);  // 자바스크립트 새창 띄우기(멀티뷰) 허용 여부
        mWebSettings.setLoadWithOverviewMode(true);      // 메타태그 허용여부
        mWebSettings.setUseWideViewPort(true);           // 화면 사이즈 맟주기 허용 여부
        mWebSettings.setSupportZoom(false);              // 화면 줌 허용 여부
        mWebSettings.setBuiltInZoomControls(false);      // 화면 확대 축소 허용 여부
        mWebSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);   // 컨텐츠 사이즈 맞추기
        //mWebSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);       // 브라우저 캐시 허용 여부
        mWebSettings.setDomStorageEnabled(true);         // 로커저장소 허용 여부
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mWebSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        mContext =  getApplicationContext();
        mService = new MediaFileService(mContext);
        // 자바스크립트 등록
        mWebView.addJavascriptInterface(new OpenCallInterface(this), "bksnp");

        mWebView.clearCache(true);
        //mWebView.loadUrl("file:///android_asset/index.html");  //Constants.LOAD_URL// http://google.co.kr");  // 웹뷰에 표시할 URL
        mWebView.loadUrl(Constants.LOAD_URL);  // 웹뷰에 표시할 URL
        // Web에서 자바스크립트 alert을 허용하게 한다.
        mWebView.setWebChromeClient(new WebChromeClient(){
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return super.onJsAlert(view, url, message, result);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
//                WebView newWebView = new WebView(MainActivity.this);
//                mWebView.addView(newWebView);
//                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
//                transport.setWebView(newWebView);
//                resultMsg.sendToTarget();
//
//                newWebView.setWebChromeClient(new WebChromeClient(){
//                    @Override
//                    public void onCloseWindow(WebView window) {
//                        window.setVisibility(View.GONE);
//                        mWebView.removeView(window);
//                    }
//                });
//
//                return true;


//                WebView newWebView = new WebView(MainActivity.this);
//                WebSettings webSettings = newWebView.getSettings();
//                webSettings.setJavaScriptEnabled(true);
//                final Dialog dialog = new Dialog(MainActivity.this);
//                dialog.setContentView(newWebView); dialog.show();
//                newWebView.setWebChromeClient(new WebChromeClient() {
//                    @Override public void onCloseWindow(WebView window) {
//                        dialog.dismiss();
//                    }
//                });
//                ((WebView.WebViewTransport)resultMsg.obj).setWebView(newWebView);
//                resultMsg.sendToTarget();
//                return true;

// Dialog Create Code
                WebView newWebView = new WebView(MainActivity.this);
                WebSettings webSettings = newWebView.getSettings();
                webSettings.setJavaScriptEnabled(true);

                final Dialog dialog = new Dialog(MainActivity.this);
                dialog.setContentView(newWebView);

                ViewGroup.LayoutParams params = dialog.getWindow().getAttributes();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                dialog.getWindow().setAttributes((WindowManager.LayoutParams) params);
                dialog.show();
                newWebView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onCloseWindow(WebView window) {
                        dialog.dismiss();
                    }
                });

                // WebView Popup에서 내용이 안보이고 빈 화면만 보여 아래 코드 추가
                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        return false;
                    }
                });

                ((WebView.WebViewTransport)resultMsg.obj).setWebView(newWebView);
                resultMsg.sendToTarget();
                return true;
            };

        });

        // 페이지 로딩이 완료되었을 때
        mWebView.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageFinished(WebView view, String url) {
                if (mDeepLinkUrl != null) {
                    mWebView.loadUrl("javascript:setSharedLinkData('"+ mDeepLinkUrl +"')");
                    mDeepLinkUrl = null;
                }
                if (mMessage != null) {
                    mWebView.loadUrl("javascript:getNotification('" + mMessage + "')");
                    mMessage = null;
                }
            }
//            @Override
//            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
//                WebView newWebView = new WebView(MainActivity.this);
//                WebSettings webSettings = newWebView.getSettings();
//                webSettings.setJavaScriptEnabled(true);
//                final Dialog dialog = new Dialog(MainActivity.this);
//                dialog.setContentView(newWebView); dialog.show();
//                newWebView.setWebChromeClient(new WebChromeClient() {
//                    @Override
//                    public void onCloseWindow(WebView window) {
//                        dialog.dismiss();
//                    }
//                });
//                ((WebView.WebViewTransport)resultMsg.obj).setWebView(newWebView);
//                resultMsg.sendToTarget();
//                return true;
//            }

        });
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(getIntent())
                .addOnSuccessListener(this, new OnSuccessListener<PendingDynamicLinkData>() {
                    @Override
                    public void onSuccess(PendingDynamicLinkData pendingDynamicLinkData) {
                        Uri deepLink = null;
                        try {
                            if (pendingDynamicLinkData != null) {
                                deepLink = pendingDynamicLinkData.getLink();
                                //mWebView.loadUrl(deepLink.toString());
                                Log.d(TAG, "deepLink => " + deepLink.toString());
                                mDeepLinkUrl = deepLink.toString();
                                //mWebView.loadUrl("javascript:setSharedLinkData('"+ deepLink.toString() +"')");
                            }
                        }
                        catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("링크/에러", String.valueOf(e));
                    }
                });

        // 공유 디렉토리 생성
        String folderName = "Teengle";
        //File dir = Environment.getExternalStoragePublicDirectory("/ohnion");
        File dir = new File(Environment.getExternalStorageDirectory(), folderName);
        //File dir = new File(mContext.getFilesDir().getAbsolutePath()+File.pathSeparator + "ohnion");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        mDirname = dir.getAbsolutePath();
    }

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//
//            if (requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
//                super.onActivityResult(requestCode, resultCode, data);
//                return;
//            }
//            Uri[] results = null;
//
//            // Check that the response is a good one
//            if (resultCode == Activity.RESULT_OK) {
//                if (data == null) {
//                    // If there is not data, then we may have taken a photo
//                    if (mCameraPhotoPath != null) {
//                        results = new Uri[]{Uri.parse(mCameraPhotoPath)};
//                    }
//                } else {
//                    String dataString = data.getDataString();
//                    if (dataString != null) {
//                        results = new Uri[]{Uri.parse(dataString)};
//                    }
//                }
//            }
//            mFilePathCallback.onReceiveValue(results);
//            mFilePathCallback = null;
//
//        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
//            if (requestCode != FILECHOOSER_RESULTCODE || mUploadMessage == null) {
//                super.onActivityResult(requestCode, resultCode, data);
//                return;
//            }
//            if (requestCode == FILECHOOSER_RESULTCODE) {
//
//                if (null == this.mUploadMessage) {
//                    return;
//                }
//                Uri result = null;
//                try {
//                    if (resultCode != RESULT_OK) {
//                        result = null;
//                    } else {
//                        // retrieve from the private variable if the intent is null
//                        result = data == null ? mCapturedImageURI : data.getData();
//                    }
//                } catch (Exception e) {
//                    Toast.makeText(getApplicationContext(), "activity :" + e,
//                            Toast.LENGTH_LONG).show();
//                }
//
//                mUploadMessage.onReceiveValue(result);
//                mUploadMessage = null;
//            }
//        }
//        return;
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_CANCELED) return;

        System.out.println("onActivityResult called..........");
        Log.d(TAG, "requestCode => " + requestCode);
        if (requestCode == Constants.REQUEST_CAMERA) {
            //Uri uri = getLastCaptureImageUri();
            Log.d(TAG, "Camera => " + mFileUri);
            //jpg형식
            //Log.d(TAG, "Camera data => " + mService.base64ReadData(mService.getPath()));
            String snd = null;
            //synchronized (this) {
            try {
                snd = mService.base64ReadData(mService.getPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "Camera data => " + snd);
            Log.d(TAG, "length => " + snd.length());

            // data를 분리한다.
            if (snd.length() > 512) {
                int index = 0;
                int readLength = 513;
                String readData = "";
                while(true) {
                    readData = snd.substring(index, readLength);
                    mWebView.loadUrl("javascript:addCameraData('"+ readData +"')");

                    index = readLength;
                    if (snd.length() > (index+512)) {
                        readLength += 513;
                        continue;
                    }else {
                        readData = snd.substring(index);
                        mWebView.loadUrl("javascript:addCameraData('"+ readData +"')");
                        break;
                    }
                }
                mWebView.loadUrl("javascript:setCameraData('')");
            }else {
                mWebView.loadUrl("javascript:addCameraData('"+ snd +"')");
                mWebView.loadUrl("javascript:setCameraData('')");
            }
//            String finalSnd = snd;
//            mHandler.post(new Runnable() {
//                @Override
//                public void run() {
//                    Log.d(TAG, "Camera data => " + finalSnd);
//                    mWebView.loadUrl("javascript:setCameraData('"+ finalSnd +"')");
//                    mWebView.loadUrl("javascript:setCameraData('test2')");
//                }
//            });
        }else if (requestCode == Constants.REQUEST_ALBUM) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Uri uri = data.getData();
                    Log.d(TAG, "Album => " + uri);
                    //Log.d(TAG, "Album data => " + mService.base64ReadData(mService.getPathFromUri(uri)));
                    String snd = null;
                    try {
                        snd = mService.base64ReadData(mService.getPathFromUri(uri));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "Album data => " +snd);
                    Log.d(TAG, "length => " + snd.length());
                    // data를 분리한다.
                    if (snd.length() > 512) {
                        int index = 0;
                        int readLength = 513;
                        String readData = "";
                        while(true) {
                            readData = snd.substring(index, readLength);
                            mWebView.loadUrl("javascript:addAlbumData('"+ readData +"')");

                            index = readLength;
                            if (snd.length() > (index+512)) {
                                readLength += 513;
                                continue;
                            }else {
                                readData = snd.substring(index);
                                mWebView.loadUrl("javascript:addAlbumData('"+ readData +"')");
                                break;
                            }
                        }
                        mWebView.loadUrl("javascript:setAlbumData('')");
                    }else {
                        mWebView.loadUrl("javascript:addAlbumData('"+ snd +"')");
                        mWebView.loadUrl("javascript:setAlbumData('')");
                    }
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mMessageReceiver, new IntentFilter(String.valueOf(R.string.bksnp_event_name))  // "bksnp-event")
        );
        Log.d(TAG, "activated...");
        mWebView.loadUrl("javascript:setActivate()");
    }
    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        Log.d(TAG, "deactivated...");
        mWebView.loadUrl("javascript:setDeactivate()");
    }
    public void getMessage(String msg) {
        Log.d(TAG, msg);
    }

    /**
     * API 오픈
     */
    public class OpenCallInterface {
        Context context;
        OpenCallInterface(Context c) {
            context = c;
        }

        /**
         * 앨범 Open
         */
        @JavascriptInterface
        public void callAlbum() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent( Intent.ACTION_PICK );
                    intent.setType( MediaStore.Images.Media.CONTENT_TYPE );
                    intent.setData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult( intent, Constants.REQUEST_ALBUM );
                }
            });
        }

        /**
         * 카메라 오픈
         */
        @JavascriptInterface
        public void callCamera() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // create Intent to take a picture and return control to the calling application
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                    mFileUri = mService.getOutputMediaFileUri(MediaFileService.MEDIA_TYPE_IMAGE); // create a file to save the image
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, mFileUri); // set the image file name

                    // start the image capture Intent
                    startActivityForResult(intent, Constants.REQUEST_CAMERA);

//                    Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
//                    try {
//                        PackageManager pm = getPackageManager();
//
//                        final ResolveInfo mInfo = pm.resolveActivity(i, 0);
//
//                        Intent intent = new Intent();
//                        intent.setComponent(new ComponentName(mInfo.activityInfo.packageName, mInfo.activityInfo.name));
//                        intent.setAction(Intent.ACTION_MAIN);
//                        intent.addCategory(Intent.CATEGORY_LAUNCHER);
//
//                        Uri fileUri = mService.getOutputMediaFileUri(MediaFileService.MEDIA_TYPE_IMAGE);
//                        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
//
//                        startActivityForResult(intent, Constants.REQUEST_CAMERA);
//                    } catch (Exception e){ Log.i("TAG", "Unable to launch camera: " + e); }
                }
            });
        }

        /**
         * Internal File write
         * @param fileKey
         * @param data
         */
        @JavascriptInterface
        public void callWriteStorage(String fileKey, String data) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.i("BKSNP", "callWriteStorage call");
                    Log.i("BKSNP", "Parameter fileKey : " + fileKey+", data : " + data);
                    FileOutputStream fos = null;
                    try {
                        fos = openFileOutput(fileKey, Context.MODE_PRIVATE);
                        fos.write(data.getBytes());
                        fos.close();;

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        /**
         * Internal File read
         * @param fileKey
         */
        @JavascriptInterface
        public String callReadStorage(String fileKey) {
            //mHandler.post(new Runnable() {
            //    @Override
            //    public void run() {
                    Log.i("BKSNP", "callReadStorage call");
                    Log.i("BKSNP", "Parameter fileKey : " + fileKey);
                    StringBuffer buffer = new StringBuffer();
                    String data = null;
                    FileInputStream fis = null;
                    try {
                        fis = openFileInput(fileKey);
                        BufferedReader iReader = new BufferedReader(new InputStreamReader((fis)));

                        data = iReader.readLine();
                        while(data != null)
                        {
                            buffer.append(data);
                            data = iReader.readLine();
                        }
                        buffer.append("\n");
                        iReader.close();
                        Log.i("BKSNP", "read data : " + buffer.toString());
                        //mWebView.loadUrl("javascript:setReadStorage('"+buffer.toString()+"')");

                        return buffer.toString();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return "";
            //    }
            //});
        }

        /**
         * Base64 Internal File write
         * @param fileKey
         * @param data
         */
        @JavascriptInterface
        public void callBase64WriteStorage(String fileKey, String data) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.i("BKSNP", "callBase64WriteStorage call");
                    Log.i("BKSNP", "Parameter fileKey : " + fileKey+", data : " + data);
                    FileOutputStream fos = null;
                    try {
                        fos = openFileOutput(fileKey, Context.MODE_PRIVATE);
                        fos.write(data.getBytes());
                        fos.close();;

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        /**
         * Base64 Internal File read
         * @param fileKey
         */
        @JavascriptInterface
        public String callBase64ReadStorage(String fileKey) {
            //mHandler.post(new Runnable() {
           //     @Override
           //     public void run() {
                    Log.i("BKSNP", "callBase64ReadStorage call");
                    Log.i("BKSNP", "Parameter fileKey : " + fileKey);
                    StringBuffer buffer = new StringBuffer();
                    String data = null;
                    FileInputStream fis = null;
                    try {
                        fis = openFileInput(fileKey);
                        BufferedReader iReader = new BufferedReader(new InputStreamReader((fis)));

                        data = iReader.readLine();
                        while(data != null)
                        {
                            buffer.append(data);
                            data = iReader.readLine();
                        }
                        buffer.append("\n");
                        iReader.close();
                        Log.i("BKSNP", "read data : " + buffer.toString());
                        //mWebView.loadUrl("javascript:setBase64ReadStorage('"+buffer.toString()+"')");
                        return buffer.toString();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return "";
           //     }
           // });
        }

        /**
         * Teengle Base64 Internal File write
         * @param fileKey
         * @param data
         */
        @JavascriptInterface
        public void callTeengleBase64WriteStorage(String fileKey, String data) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.i("BKSNP", "callTeengleBase64WriteStorage call");
                    Log.i("BKSNP", "Parameter fileKey : " + fileKey+", data : " + data);
                    FileOutputStream fos = null;
                    try {
                        File dir = new File(Environment.getExternalStorageDirectory(), "Teengle");
                        File file = new File(dir, fileKey);
                        if (file.exists())
                            file.delete();

                        //fos = openFileOutput(fileKey, Context.MODE_PRIVATE);
                        fos = new FileOutputStream(file);
                        fos.write(data.getBytes());
                        fos.close();;

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        /**
         * Teengle Base64 Internal File read
         * @param fileKey
         */
        @JavascriptInterface
        public String callTeengleBase64ReadStorage(String fileKey) {
            Log.i("BKSNP", "callTeengleBase64ReadStorage call");
            Log.i("BKSNP", "Parameter fileKey : " + fileKey);
            StringBuffer buffer = new StringBuffer();
            String data = null;
            FileInputStream fis = null;
            try {
                File dir = new File(Environment.getExternalStorageDirectory(), "Teengle");
                File file = new File(dir, fileKey);
                //fis = openFileInput(fileKey);
                fis = new FileInputStream(file);
                BufferedReader iReader = new BufferedReader(new InputStreamReader((fis)));

                data = iReader.readLine();
                while(data != null)
                {
                    buffer.append(data);
                    data = iReader.readLine();
                }
                buffer.append("\n");
                iReader.close();
                Log.i("BKSNP", "read data : " + buffer.toString());
                return buffer.toString();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return "";
        }

        /**
         * Device Key return
         */
        @JavascriptInterface
        public String callDeviceKey() {
            //mHandler.post(new Runnable() {
            //    @Override
            //    public void run() {
                    String key = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                    Log.i("BKSNP", "Device key : " + key);
                    //mWebView.loadUrl("javascript:getDeviceKey('"+key+"')");
                    return key;
           //     }
           // });
        }

        /**
         * Application cache read
         */
        @JavascriptInterface
        public String callCacheRead(String key) {
            //mHandler.post(new Runnable() {
            //    @Override
            //    public void run() {
                    File cacheFile = new File(context.getCacheDir(), key);
                    try {
                        Scanner scanner = new Scanner(cacheFile);
                        StringBuffer sb = new StringBuffer();
                        while(scanner.hasNext()) {
                            String str = scanner.nextLine();
                            sb.append(str);
                        }
                        //mWebView.loadUrl("javascript:readCache('"+sb.toString()+"')");
                        return sb.toString();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    return "";
            //    }
            //});
        }

        /**
         * firebase token data read
         */
        @JavascriptInterface
        public String callFirebaseToken() {
            //mHandler.post(new Runnable() {
            //    @Override
            //    public void run() {
            //        mWebView.loadUrl("javascript:readFirebaseToken('"+mToken+"')");
            //    }
            //});
            return mToken;
        }

        @JavascriptInterface
        public void test() {
            Log.i("BKSNP", "test called...");
        }

        @JavascriptInterface
        public void sharedTeengle(String subject, String url, String image_url) {
            Log.d(TAG, "sharedTeengle called...");
            Create_DynamicLink(subject, url, image_url);
//            mHandler.post(new Runnable() {
//                @Override
//                public void run() {
//                    Create_DynamicLink(subject, url, image_url);
//                }
//            });
        }

        /**
         * 디렉토리명 리턴
         * @return
         */
        @JavascriptInterface
        public String callSharedDir() {
            String dirname = mDirname;
            Log.i("BKSNP", "Dir name : " + dirname);
            return dirname;
        }

        @JavascriptInterface
        public void appClose() {
            finishAffinity();
            System.exit(0);
        }

    }

    /**
     * 뒤로가기 버튼 제어
     */
    @Override
    public void onBackPressed() {
        runOnUiThread(new Runnable() {
                          @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                          @Override
                          public void run() {
                              Log.i("BKSNP", "onBackPressed call");
                              //mWebView.evaluateJavascript("setBackButton()", null);  // loadUrl("javascript:setBackButton()");
                              mWebView.loadUrl("javascript:setBackButton()");
                          }
                      });
    }

    /**
     * Firebase read message
     */
    private void readFirebase() {
        //mDatabase.child("msg").child("1").addValueEventListener(new ValueEventListener() {
        mRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                Log.w("FireBaseData", "onChildAdded called..");
                String msg = dataSnapshot.getValue(String.class);
                Log.w("FireBaseData", msg);
//                for(DataSnapshot ds : dataSnapshot.getChildren()) {
//                    String msg = ds.getValue(String.class); // ds.child("data").getValue(String.class);
                    Log.w("FireBaseData", "getData =>" + msg);
                    mWebView.loadUrl("javascript:getNotification('"+msg+"')");
                    NotificationCompat.Builder builder = null;
                    String channel_id_set = "";
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        channel_id_set = Constants.CHANNEL_ID;
                    }
                    builder = new NotificationCompat.Builder(mContext, channel_id_set)
                            .setSmallIcon(R.drawable.notification_icon)
                            .setContentTitle("BKSNP 알림")
                            .setContentText(msg)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);

                    // notificationId is a unique int for each notification that you must define
                    Random notification_id = new Random();
                    notificationManager.notify(notification_id.nextInt(100), builder.build());
                //}
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Log.w("FireBaseData", "onChildChanged called..");
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                Log.w("FireBaseData", "onChildRemoved called..");
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Log.w("FireBaseData", "onChildMoved called..");
            }

//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                // Get Post object and use the values to update the UI
//
//                for(DataSnapshot ds : dataSnapshot.getChildren()) {
//                    String msg = ds.getValue(String.class); // ds.child("data").getValue(String.class);
//                    Log.w("FireBaseData", "getData =>" + msg);
//                    mWebView.loadUrl("javascript:getNotification('"+msg+"')");
//                    NotificationCompat.Builder builder = null;
//                    String channel_id_set = "";
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                        channel_id_set = channel_id;
//                    }
//                    builder = new NotificationCompat.Builder(mContext, channel_id_set)
//                            .setSmallIcon(R.drawable.notification_icon)
//                            .setContentTitle("BKSNP 알림")
//                            .setContentText(msg)
//                            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
//                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
//
//                    // notificationId is a unique int for each notification that you must define
//                    Random notification_id = new Random();
//                    notificationManager.notify(notification_id.nextInt(100), builder.build());
//                }

//                if(dataSnapshot.getValue(String.class) != null){
//                    String msg = dataSnapshot.getValue(String.class);
//                    Log.w("FireBaseData", "getData =>" + msg);
//                    mWebView.loadUrl("javascript:getNotification('"+msg+"')");
//                } else {
//                    Toast.makeText(MainActivity.this, "데이터 없음...", Toast.LENGTH_SHORT).show();
//                }
//            }
//
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Getting Post failed, log a message
                Log.w("FireBaseData", "loadPost:onCancelled", databaseError.toException());
            }
        });


    }

    public void Create_DynamicLink(final String subject, String PageURL, String ImgUrl){
        Log.d(TAG, getPackageName());
        Task<ShortDynamicLink> shortLinkTask = FirebaseDynamicLinks.getInstance().createDynamicLink()
                .setLink(Uri.parse(PageURL))
                .setDomainUriPrefix("https://ohnion.page.link/")  // eNh4")
                .setAndroidParameters(
                        new DynamicLink.AndroidParameters.Builder(getPackageName())
                                .build())
                .setSocialMetaTagParameters(
                        new DynamicLink.SocialMetaTagParameters.Builder()
                                .setTitle("친구에게 공유하기")
                                .setImageUrl(Uri.parse(ImgUrl))
                                .build())
                .buildShortDynamicLink()
                .addOnCompleteListener(this, new OnCompleteListener<ShortDynamicLink>() {
                    @Override
                    public void onComplete(@NonNull Task<ShortDynamicLink> task) {
                        Log.d(TAG, "task called...");
                        if (task.isSuccessful()) {
                            Uri ShortLink = task.getResult().getShortLink();
                            try {
                                Intent Sharing_Intent = new Intent();
                                Sharing_Intent.setAction(Intent.ACTION_SEND);
                                Sharing_Intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                                Sharing_Intent.putExtra(Intent.EXTRA_TEXT, ShortLink.toString());
                                Sharing_Intent.setType("text/plain");
                                startActivity(Intent.createChooser(Sharing_Intent, "친구에게 공유하기"));
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                        }else {
                            Log.d(TAG, task.getException().toString());
                        }
                    }
                });
    }
}

