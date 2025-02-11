package com.dji.a0117videotest;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import dji.sdk.sdkmanager.LiveStreamManager;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private static final String TAG = MainActivity.class.getName();
    private static final int REQUEST_PERMISSION_CODE = 12345;

    private BaseProduct mProduct;
    private DJICodecManager codecManager;
    private LiveStreamManager liveStreamManager; // DJI 官方 RTMP 推流模块
    private boolean isStreaming = false;
    private String rtmpUrl = "";

    private TextureView videoSurface;
    private TextView statusText;
    private TextView rtmpUrlTitle;
    private Button startStreamBtn;

    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.RECORD_AUDIO // 如果要推流音频
    };

    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkAndRequestPermissions();
        }

        // 初始化 UI 控件
        videoSurface = findViewById(R.id.video_surface);
        statusText = findViewById(R.id.statusText);
        rtmpUrlTitle = findViewById(R.id.rtmp_url_title);
        startStreamBtn = findViewById(R.id.startStreamBtn);

        videoSurface.setSurfaceTextureListener(this);

        // 按钮点击事件：开始/停止推流
        startStreamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStreaming();
            }
        });

        showStatus("等待 DJI 设备连接...");
    }

    /**
     * 检查并请求所需权限
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
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
            showStatus("缺少权限，无法继续");
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                @Override
                public void onRegister(DJIError djiError) {
                    if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                        DJISDKManager.getInstance().startConnectionToProduct();
                        showStatus("SDK 注册成功，连接 DJI 设备...");
                    } else {
                        showStatus("SDK 注册失败：" + djiError.getDescription());
                    }
                }

                @Override
                public void onProductConnect(@NonNull BaseProduct baseProduct) {
                    showStatus("DJI 设备已连接");
                }

                @Override
                public void onProductDisconnect() {
                    showStatus("DJI 设备已断线");
                    stopStreaming();
                }
                @Override
                public void onProductChanged(BaseProduct baseProduct) { // 这里补充实现
                    showStatus("DJI 设备已更换：" +
                            (baseProduct != null ? baseProduct.getModel().getDisplayName() : "未知设备"));
                }

                @Override
                public void onComponentChange(BaseProduct.ComponentKey componentKey,
                                              dji.sdk.base.BaseComponent oldComponent, dji.sdk.base.BaseComponent newComponent) {
                    showStatus("组件变更：" + componentKey.toString());
                }

                @Override
                public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {
                    showStatus("SDK 初始化中：" + djisdkInitEvent.toString());
                }

                @Override
                public void onDatabaseDownloadProgress(long current, long total) {
                    showStatus("数据库下载进度：" + current + "/" + total);
                }
            });
        }
    }

    private void toggleStreaming() {
        if (isStreaming) {
            stopStreaming();
        } else {
            if (rtmpUrl.isEmpty()) {
                showUrlInputDialog();
            } else {
                startStreaming();
            }
        }
    }

    private void startStreaming() {
        liveStreamManager = DJISDKManager.getInstance().getLiveStreamManager();
        if (liveStreamManager != null) {
            liveStreamManager.setLiveUrl(rtmpUrl);
            liveStreamManager.isLiveAudioEnabled(); // 启用音频推流
            int result = liveStreamManager.startStream();
            if (result == 0) {
                showStatus("RTMP 推流已启动");
                isStreaming = true;
                startStreamBtn.setText("停止 RTMP 推流");
            } else {
                showStatus("RTMP 推流启动失败: " + result);
            }
        } else {
            showStatus("无法获取 LiveStreamManager");
        }
    }

    private void stopStreaming() {
        if (isStreaming && liveStreamManager != null) {
            liveStreamManager.stopStream();
            showStatus("RTMP 推流已停止");
            isStreaming = false;
            startStreamBtn.setText("开始 RTMP 推流");
        }
    }

    private void showUrlInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("请输入 RTMP URL");

        final EditText input = new EditText(MainActivity.this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                rtmpUrl = url;
                rtmpUrlTitle.setText("RTMP URL: " + rtmpUrl);
                rtmpUrlTitle.setVisibility(View.VISIBLE);
                startStreaming();
            } else {
                showStatus("RTMP URL 不能为空！");
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showStatus(final String message) {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText("状态：" + message);
            }
        });
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (codecManager == null) {
            codecManager = new DJICodecManager(this, surface, width, height);
        }
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
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}
}
