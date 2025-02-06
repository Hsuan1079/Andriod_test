package com.dji.a0117videotest;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKManager;

public class WelcomeActivity extends AppCompatActivity {

    private TextView statusText;
    private Button openButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        statusText = findViewById(R.id.statusText);
        openButton = findViewById(R.id.openButton);

        // 初始化 DJI SDK
        initializeDJISDK();

        // 按下 "Open" 檢查 DJI 設備狀態並進入主畫面
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkConnectionAndProceed();
            }
        });
    }

    private void initializeDJISDK() {
        DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
            @Override
            public void onRegister(DJIError djiError) {
                if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                    showStatus("SDK 註冊成功，正在連接 DJI 設備...");
                    DJISDKManager.getInstance().startConnectionToProduct();
                } else {
                    showStatus("SDK 註冊失敗：" + djiError.getDescription());
                }
            }

            @Override
            public void onProductConnect(BaseProduct baseProduct) {
                showStatus("DJI 設備已連接：" + (baseProduct != null ? baseProduct.getModel().getDisplayName() : "未知設備"));
            }

            @Override
            public void onProductDisconnect() {
                showStatus("DJI 設備已斷線");
            }

            @Override
            public void onProductChanged(BaseProduct baseProduct) {}

            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey, dji.sdk.base.BaseComponent oldComponent, dji.sdk.base.BaseComponent newComponent) {}

            @Override
            public void onInitProcess(dji.sdk.sdkmanager.DJISDKInitEvent djisdkInitEvent, int progress) {}

            @Override
            public void onDatabaseDownloadProgress(long current, long total) {}
        });
    }

    private void checkConnectionAndProceed() {
        BaseProduct product = DJISDKManager.getInstance().getProduct();
        if (product != null && product.isConnected()) {
            // DJI 設備已連接，進入主畫面
            showStatus("設備已連接，進入影像畫面...");
            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            startActivity(intent);
        } else {
            // DJI 設備未連接
            showStatus("未檢測到 DJI 設備，請檢查連接狀態");
        }
    }

    private void showStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText(status);
            }
        });
    }
}
