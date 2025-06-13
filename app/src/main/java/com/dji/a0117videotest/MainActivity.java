package com.dji.a0117videotest;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
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
    private Button resetRtmpBtn;
    private Button resetTcpBtn;
    // private Button btnOpenControl;

    // Direction control buttons
    private Button btnForward;
    private Button btnBackward;
    private Button btnLeft;
    private Button btnRight;
    private Button btnUp;
    private Button btnDown;
    private Button btnRotateLeft;
    private Button btnRotateRight;

    private TCPServer tcpServer;
    private DroneController droneController;

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
        statusText = findViewById(R.id.statusTextView);
        rtmpUrlTitle = findViewById(R.id.rtmp_url_title);
        startStreamBtn = findViewById(R.id.startStreamBtn);
        resetRtmpBtn = findViewById(R.id.resetRtmpBtn);
        resetTcpBtn = findViewById(R.id.resetTcpBtn);

        // Initialize direction control buttons
        btnForward = findViewById(R.id.btn_forward);
        btnBackward = findViewById(R.id.btn_backward);
        btnLeft = findViewById(R.id.btn_left);
        btnRight = findViewById(R.id.btn_right);
        btnUp = findViewById(R.id.btn_up);
        btnDown = findViewById(R.id.btn_down);
        btnRotateLeft = findViewById(R.id.btn_rotate_left);
        btnRotateRight = findViewById(R.id.btn_rotate_right);
        // btnOpenControl = findViewById(R.id.btn_open_control);

        videoSurface.setSurfaceTextureListener(this);

        // 初始化 DroneController 和 TCPServer
        droneController = new DroneController();
        tcpServer = new TCPServer(droneController, this);
        tcpServer.start();  // 启动 TCP 服务器

        setupDirectionButtons();

        // 按钮点击事件：开始/停止推流
        startStreamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStreaming();
            }
        });

        // RTMP 重置按钮点击事件
        resetRtmpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetRtmpConnection();
            }
        });

        // TCP 重置按钮点击事件
        resetTcpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetTcpConnection();
            }
        });

        /*showStatus("等待 DJI 設備連接...");
        btnOpenControl.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ControlActivity.class);
            startActivity(intent);
        });*/
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpServer != null) {
            tcpServer.stopServer();
        }
        if (isStreaming && liveStreamManager != null) {
            liveStreamManager.stopStream();
        }
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
                        showStatus("SDK 註冊成功，連接設備...");
                    } else {
                        showStatus("SDK 註冊失败：" + djiError.getDescription());
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
                    showStatus("DJI 設備已更換：" +
                            (baseProduct != null ? baseProduct.getModel().getDisplayName() : "未知设备"));
                }

                @Override
                public void onComponentChange(BaseProduct.ComponentKey componentKey,
                                              dji.sdk.base.BaseComponent oldComponent, dji.sdk.base.BaseComponent newComponent) {
                    showStatus("組件變更：" + componentKey.toString());
                }

                @Override
                public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {
                    showStatus("SDK 初始化中：" + djisdkInitEvent.toString());
                }

                @Override
                public void onDatabaseDownloadProgress(long current, long total) {
                    showStatus("下載速度：" + current + "/" + total);
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
                showStatus("RTMP streamimg started");
                isStreaming = true;
                startStreamBtn.setText("stop streaming");
            } else {
                showStatus("RTMP streaming failure: " + result);
            }
        } else {
            showStatus("can't get LiveStreamManager");
        }
    }

    private void stopStreaming() {
        if (isStreaming && liveStreamManager != null) {
            liveStreamManager.stopStream();
            showStatus("RTMP stop streaming");
            isStreaming = false;
            startStreamBtn.setText("Start RTMP Streaming");
        }
    }

    private void showUrlInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("请输入 RTMP URL");

        final EditText input = new EditText(MainActivity.this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        builder.setView(input);

        builder.setPositiveButton("確定", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                rtmpUrl = url;
                rtmpUrlTitle.setText("RTMP URL: " + rtmpUrl);
                rtmpUrlTitle.setVisibility(View.VISIBLE);
                startStreaming();
            } else {
                showStatus("RTMP URL can't be empty！");
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    public void showStatus(String message) {
        TextView statusTextView = findViewById(R.id.statusTextView);
        ScrollView scrollView = (ScrollView) statusTextView.getParent();
        
        // Append the new message
        statusTextView.append(message + "\n");
        
        // Scroll to the bottom
        scrollView.post(() -> {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        });
    }

    private void resetRtmpConnection() {
        // 停止当前流
        if (isStreaming && liveStreamManager != null) {
            liveStreamManager.stopStream();
            isStreaming = false;
        }
        
        // 重置 RTMP URL
        rtmpUrl = "";
        rtmpUrlTitle.setText("RTMP URL: ");
        rtmpUrlTitle.setVisibility(View.GONE);
        startStreamBtn.setText("开始 RTMP 推流");
        
        showStatus("RTMP 连接已重置");
    }

    private void resetTcpConnection() {
        // 停止当前 TCP 服务器
        if (tcpServer != null) {
            tcpServer.stopServer();
        }
        
        // 创建新的 TCP 服务器实例
        tcpServer = new TCPServer(droneController, this);
        tcpServer.start();
        
        showStatus("TCP 服务器已重置");
    }

    private void setupDirectionButtons() {
        // Forward
        btnForward.setOnClickListener(v -> processCommand(0.0f, 0.6f, 0.0f, 0.0f, "Forward"));
        
        // Backward
        btnBackward.setOnClickListener(v -> processCommand(0.0f, -0.6f, 0.0f, 0.0f, "Backward"));
        
        // Left
        btnLeft.setOnClickListener(v -> processCommand(-0.6f, 0.0f, 0.0f, 0.0f, "Left"));
        
        // Right
        btnRight.setOnClickListener(v -> processCommand(0.6f, 0.0f, 0.0f, 0.0f, "Right"));
        
        // Up
        btnUp.setOnClickListener(v -> processCommand(0.0f, 0.0f, 0.0f, 0.6f, "Up"));
        
        // Down
        btnDown.setOnClickListener(v -> processCommand(0.0f, 0.0f, 0.0f, -0.6f, "Down"));
        
        // Rotate Left
        btnRotateLeft.setOnClickListener(v -> processCommand(0.0f, 0.0f, -8.0f, 0.0f, "Rotate Left"));
        
        // Rotate Right
        btnRotateRight.setOnClickListener(v -> processCommand(0.0f, 0.0f, 8.0f, 0.0f, "Rotate Right"));
    }

    /**
     * Process a command and log it
     * @param dy Left/Right distance (±0.6 m)
     * @param dx Forward/Backward distance (±0.6 m)
     * @param dr Rotation angle (±8.0 rad)
     * @param dz Up/Down distance (±0.6 m)
     * @param source Source of the command (for logging)
     */
    public void processCommand(float dy, float dx, float dr, float dz, String source) {
        // Format the command string
        String command = String.format("2,Custom,%.1f,%.1f,%.1f,%.1f", dy, dx, dr, dz);
        
        // Log the command
        showStatus(String.format("[%s] Command: %s", source, command));
        
        // Process the command using the existing method
        if (droneController != null) {
            droneController.processDistanceCommand(dy, dx, dr, dz);
        }
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
