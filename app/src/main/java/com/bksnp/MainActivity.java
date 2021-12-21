package com.bksnp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private WebView mWebView;    // WebView define
    private WebSettings mWebSettings;   // WebView setting
    private final Handler mHandler = new Handler();

    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_ALBUM = 101;

    private Context mContext;

    //@SuppressLint("JavascriptInterface")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
}

