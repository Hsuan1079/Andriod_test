package com.dji.a0117videotest;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private static final String TAG = MainActivity.class.getName();
    private static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private static final int REQUEST_PERMISSION_CODE = 12345;

    private BaseProduct mProduct;
    private Handler mHandler;
    private DJICodecManager codecManager;
    private VideoStreamSender videoStreamSender;

    private TextureView videoSurface;
    private TextView statusText;
    private Button startStreamBtn;
    private boolean isStreaming = false; // 記錄是否正在串流

    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };

    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 檢查權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkAndRequestPermissions();
        }

        // 初始化 UI
        videoSurface = findViewById(R.id.video_surface);
        statusText = findViewById(R.id.statusText);
        startStreamBtn = findViewById(R.id.startStreamBtn);

        videoSurface.setSurfaceTextureListener(this);

        // 初始化 UDP 影像串流
        String targetIP = "192.168.1.100";  // 你的電腦 IP
        int targetPort = 5000;              // 你的電腦 UDP 接收端口
        videoStreamSender = new VideoStreamSender(targetIP, targetPort, this);

        // 設定按鈕點擊事件 (開始/停止串流)
        startStreamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStreaming();
            }
        });

        showStatus("等待 DJI 設備連接...");
    }

    /**
     * 檢查並請求權限
     */
    private void checkAndRequestPermissions() {
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        if (!missingPermission.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[0]),
                    REQUEST_PERMISSION_CODE);
        } else {
            startSDKRegistration();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showStatus("缺少權限，無法繼續");
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                @Override
                public void onRegister(DJIError djiError) {
                    if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                        DJISDKManager.getInstance().startConnectionToProduct();
                        showStatus("SDK 註冊成功，連接 DJI 設備...");
                    } else {
                        showStatus("SDK 註冊失敗：" + djiError.getDescription());
                    }
                }

                @Override
                public void onProductConnect(@NonNull BaseProduct baseProduct) {
                    showStatus("DJI 設備已連接");
                }

                @Override
                public void onProductDisconnect() {
                    showStatus("DJI 設備已斷線");
                    stopStreaming();
                }
                @Override
                public void onProductChanged(BaseProduct baseProduct) {
                    // 🔹 這個方法現在是必須的
                    showStatus("DJI 設備狀態變更：" + (baseProduct != null ? baseProduct.getModel().getDisplayName() : "未知設備"));
                }

                @Override
                public void onComponentChange(BaseProduct.ComponentKey componentKey, dji.sdk.base.BaseComponent oldComponent, dji.sdk.base.BaseComponent newComponent) {
                    showStatus("組件變更：" + componentKey.toString());
                }

                @Override
                public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {
                    showStatus("SDK 初始化中：" + djisdkInitEvent.toString());
                }

                @Override
                public void onDatabaseDownloadProgress(long current, long total) {
                    showStatus("資料庫下載進度：" + current + "/" + total);
                }
            });
        }
    }

    private void toggleStreaming() {
        if (isStreaming) {
            stopStreaming();
        } else {
            startStreaming();
        }
    }

    private void startStreaming() {
        if (!isStreaming) {
            showStatus("開始影像串流...");
            videoStreamSender.startStreaming();
            isStreaming = true;
            startStreamBtn.setText("停止串流");
        }
    }

    private void stopStreaming() {
        if (isStreaming) {
            showStatus("影像串流已停止");
            videoStreamSender.stopStreaming();
            isStreaming = false;
            startStreamBtn.setText("開始串流");
        }
    }

    private void showStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText("狀態：" + status);
            }
        });
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (codecManager == null) {
            codecManager = new DJICodecManager(this, surface, width, height);
        }

        // 直接綁定 Video Data Listener
        VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener((videoBuffer, size) -> {
            if (codecManager != null) {
                codecManager.sendDataToDecoder(videoBuffer, size);
            }
        });
    }



    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (codecManager != null) {
            codecManager.cleanSurface();
            codecManager = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        // 這個方法在 Surface 尺寸改變時觸發，通常不需要做額外處理
        if (codecManager != null) {
            codecManager.cleanSurface();
            codecManager = new DJICodecManager(this, surface, width, height);
        }
    }

}
