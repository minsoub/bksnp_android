package com.bksnp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
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

    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_ALBUM = 101;
    private final String channel_id = "BKSNP_CHA_ID";

    private Context mContext;
    private DatabaseReference mRef;
    private String url = "https://bksnp-ec823-default-rtdb.asia-southeast1.firebasedatabase.app";

    private String TAG = "BKSNP";

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            Log.d("receiver", "Got message: " + message);
            mWebView.loadUrl("javascript:getNotification('"+message+"')");
        }
    };
    //@SuppressLint("JavascriptInterface")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

                        // Log and toast
                        String msg = getString(R.string.msg_token_fmt, token);
                        Log.d(TAG, msg);
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });


        // WebView Start
        mWebView = (WebView) findViewById(R.id.webView);

        mWebView.setWebViewClient(new WebViewClient());  // 클릭시 새창 안뜨게

        mWebSettings = mWebView.getSettings();           // 세부 세팅 등록
        mWebSettings.setJavaScriptEnabled(true);         // 자바스크립트 허용
        //mWebSettings.setSupportMultipleWindows(true);   // 새창 띄우기 허용 여부
        //mWebSettings.setJavaScriptCanOpenWindowsAutomatically(true);  // 자바스크립트 새창 띄우기(멀티뷰) 허용 여부
        mWebSettings.setLoadWithOverviewMode(true);      // 메타태그 허용여부
        mWebSettings.setUseWideViewPort(true);           // 화면 사이즈 맟주기 허용 여부
        mWebSettings.setSupportZoom(false);              // 화면 줌 허용 여부
        mWebSettings.setBuiltInZoomControls(false);      // 화면 확대 축소 허용 여부
        mWebSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);   // 컨텐츠 사이즈 맞추기
        //mWebSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);       // 브라우저 캐시 허용 여부
        mWebSettings.setDomStorageEnabled(true);         // 로커저장소 허용 여부

        mContext =  getApplicationContext();
        // 자바스크립트 등록
        mWebView.addJavascriptInterface(new OpenCallInterface(this), "bksnp");

        mWebView.loadUrl("file:///android_asset/index.html");  // http://google.co.kr");  // 웹뷰에 표시할 URL

//        // firebase define
//        final FirebaseDatabase database = FirebaseDatabase.getInstance("https://bksnp-ec823-default-rtdb.asia-southeast1.firebasedatabase.app");
//        mRef = database.getReference("msg");
//        //mRef = FirebaseDatabase.getInstance().getReferenceFromUrl(url); // .getReference();
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            // 채널 만들기
//            String displayName = "BKSNP";
//            String descriptionText = "This is BKSNP channel";
//            int importance = NotificationManager.IMPORTANCE_DEFAULT;
//            NotificationChannel  channel = new NotificationChannel(channel_id, displayName, importance);
//            channel.setDescription(descriptionText); // 시스템에 채널 등록하기.
//            NotificationManager notificationManager = getSystemService(NotificationManager.class);
//            notificationManager.createNotificationChannel(channel);
//        }
//
//        readFirebase();

        // Web에서 자바스크립트 alert을 허용하게 한다.
        mWebView.setWebChromeClient(new WebChromeClient(){
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return super.onJsAlert(view, url, message, result);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mMessageReceiver, new IntentFilter(String.valueOf(R.string.bksnp_event_name))  // "bksnp-event")
        );
    }
    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    public void getMessage(String msg) {
        Log.d(TAG, msg);
    }

    /**
     * API 오픈
     */
    private class OpenCallInterface {
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
                    //startActivity(getPackageManager().getLaunchIntentForPackage("PACKAGENAME");
//                    Intent intent = new Intent();
//                    intent.setAction(Intent.ACTION_GET_CONTENT); // ACTION_PICK은 사용하지 말것, deprecated + formally
//                    intent.setType("image/*");
//                    ((Activity)mContext).startActivityForResult(Intent.createChooser(intent, "Get Album"), REQUEST_TAKE_ALBUM);


                    Intent intent = new Intent( Intent.ACTION_PICK );
                    intent.setType( MediaStore.Images.Media.CONTENT_TYPE );
                    startActivityForResult( intent, REQUEST_ALBUM );
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
                    Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    try {
                        PackageManager pm = getPackageManager();

                        final ResolveInfo mInfo = pm.resolveActivity(i, 0);

                        Intent intent = new Intent();
                        intent.setComponent(new ComponentName(mInfo.activityInfo.packageName, mInfo.activityInfo.name));
                        intent.setAction(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_LAUNCHER);

                        startActivity(intent);
                    } catch (Exception e){ Log.i("TAG", "Unable to launch camera: " + e); }
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
        public void callReadStorage(String fileKey) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
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
                        mWebView.loadUrl("javascript:setReadStorage('"+buffer.toString()+"')");
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
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
        public void callBase64ReadStorage(String fileKey) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
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
                        mWebView.loadUrl("javascript:setBase64ReadStorage('"+buffer.toString()+"')");
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        /**
         * Device Key return
         */
        @JavascriptInterface
        public void callDeviceKey() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    String key = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                    Log.i("BKSNP", "Device key : " + key);
                    mWebView.loadUrl("javascript:getDeviceKey('"+key+"')");
                }
            });
        }

        /**
         * Application cache read
         */
        @JavascriptInterface
        public void callCacheRead(String key) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    File cacheFile = new File(context.getCacheDir(), key);
                    try {
                        Scanner scanner = new Scanner(cacheFile);
                        StringBuffer sb = new StringBuffer();
                        while(scanner.hasNext()) {
                            String str = scanner.nextLine();
                            sb.append(str);
                        }
                        mWebView.loadUrl("javascript:readCache('"+sb.toString()+"')");
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            });
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
//
//        mHandler.post(new Runnable() {
//            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
//            @Override
//            public void run() {
//                Log.i("BKSNP", "onBackPressed call");
//                mWebView.evaluateJavascript("setBackButton()", null);  // loadUrl("javascript:setBackButton()");
//            }
//        });
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
                        channel_id_set = channel_id;
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
}

