package com.dji.a0117videotest;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 控制活動
 * 此活動負責接收伺服器命令並控制 DJI 設備
 * 命令格式: "2,Custom,dy,dx,dr,dz"
 * dy: 左右距離 (±0.6 m)
 * dx: 前後距離 (±0.6 m)
 * dr: 旋轉角度 (±8.0 rad)
 * dz: 上下距離 (±0.6 m)
 */
public class ControlActivity extends AppCompatActivity {
    private DroneController droneController;
    private Socket clientSocket;
    private boolean isConnected = false;
    private TextView statusText;
    private static final String SERVER_IP = "192.168.0.128"; // 伺服器 IP
    private static final int SERVER_PORT = 8080;             // 伺服器連接埠
    private ExecutorService executorService;
    private Handler mainHandler;

    // 距離限制
    private static final float MAX_LINEAR_DISTANCE = 0.6f;   // 最大線性距離 (m)
    private static final float MAX_ROTATION_ANGLE = 8.0f;    // 最大旋轉角度 (rad)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        // 初始化元件
        statusText = findViewById(R.id.status_text);
        droneController = new DroneController();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // 開始接收伺服器命令
        connectToServer();
    }

    private void connectToServer() {
        executorService.execute(() -> {
            try {
                clientSocket = new Socket(SERVER_IP, SERVER_PORT);
                isConnected = true;
                updateStatus("已連接到伺服器");

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream())
                );

                String command;
                while (isConnected && (command = reader.readLine()) != null) {
                    final String finalCommand = command.trim();
                    mainHandler.post(() -> {
                        updateStatus("收到命令: " + finalCommand);
                        processReceivedCommand(finalCommand);
                    });
                }
            } catch (IOException e) {
                updateStatus("連線錯誤: " + e.getMessage());
                isConnected = false;
            }
        });
    }

    /**
     * 處理從伺服器接收到的距離命令
     * @param command 格式: "2,Custom,dy,dx,dr,dz"
     */
    private void processReceivedCommand(String command) {
        try {
            // 解析命令字串
            String[] parts = command.split(",");
            if (parts.length != 6 || !parts[0].equals("2") || !parts[1].equals("Custom")) {
                updateStatus("無效的命令格式");
                return;
            }

            // 解析距離值
            float dy = parseDistance(parts[2], MAX_LINEAR_DISTANCE);  // 左右距離
            float dx = parseDistance(parts[3], MAX_LINEAR_DISTANCE);  // 前後距離
            float dr = parseDistance(parts[4], MAX_ROTATION_ANGLE);   // 旋轉角度
            float dz = parseDistance(parts[5], MAX_LINEAR_DISTANCE);  // 上下距離

            // 使用無人機控制器處理距離命令
            droneController.processDistanceCommand(dx, dy, dz, dr);
            
            updateStatus(String.format("距離命令: dx=%.2f, dy=%.2f, dz=%.2f, dr=%.2f", 
                dx, dy, dz, dr));
        } catch (Exception e) {
            updateStatus("處理命令時發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 解析距離值並確保在限制範圍內
     * @param value 距離字串
     * @param maxValue 最大允許值
     * @return 解析後的距離值
     */
    private float parseDistance(String value, float maxValue) {
        try {
            float distance = Float.parseFloat(value);
            // 確保距離在限制範圍內
            return Math.max(-maxValue, Math.min(maxValue, distance));
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private void updateStatus(String message) {
        mainHandler.post(() -> {
            if (statusText != null) {
                statusText.setText(message);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isConnected = false;
        if (clientSocket != null && !clientSocket.isClosed()) {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        executorService.shutdown();
    }
}
