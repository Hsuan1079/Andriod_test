package com.dji.a0117videotest;

import android.util.Log;
import android.os.Handler;
import dji.sdk.products.Aircraft;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.common.flightcontroller.virtualstick.FlightControlData;

public class DroneController {
    private FlightController flightController;
    private final Handler moveHandler = new Handler();
    private static final float MOVEMENT_SPEED = 0.3f; // 移動速度 (m/s)
    private static final float ROTATION_SPEED = 4.0f; // 旋轉速度 (rad/s)

    public DroneController() {
        // 確保無人機已連接
        if (DJISDKManager.getInstance().getProduct() instanceof Aircraft) {
            Aircraft aircraft = (Aircraft) DJISDKManager.getInstance().getProduct();
            flightController = aircraft.getFlightController();
        }

        if (flightController != null) {
            Log.d("DroneController", "FlightController 已初始化");
            enableVirtualStickMode();
        } else {
            Log.e("DroneController", "無人機未連接，FlightController 初始化失敗");
        }
    }

    // 啟用 Virtual Stick Mode
    private void enableVirtualStickMode() {
        if (flightController != null) {
            flightController.setVirtualStickModeEnabled(true, error -> {
                if (error == null) {
                    Log.d("DroneController", "✅ Virtual Stick Mode 啟用成功");
                    flightController.setVirtualStickAdvancedModeEnabled(true);
                } else {
                    Log.e("DroneController", "❌ Virtual Stick Mode 啟用失敗: " + error.getDescription());
                }
            });
        }
    }

    /**
     * 處理距離命令
     * @param dx 前後距離 (m)
     * @param dy 左右距離 (m)
     * @param dz 上下距離 (m)
     * @param dr 旋轉角度 (rad)
     */
    public void processDistanceCommand(float dx, float dy, float dz, float dr) {
        if (flightController == null) {
            Log.e("DroneController", "FlightController 未初始化");
            return;
        }

        // 計算移動時間（基於距離和速度）
        int moveDuration = (int) (Math.abs(dx) / MOVEMENT_SPEED * 1000);
        int strafeDuration = (int) (Math.abs(dy) / MOVEMENT_SPEED * 1000);
        int verticalDuration = (int) (Math.abs(dz) / MOVEMENT_SPEED * 1000);
        int rotationDuration = (int) (Math.abs(dr) / ROTATION_SPEED * 1000);

        // 計算方向
        float pitch = dx > 0 ? MOVEMENT_SPEED : -MOVEMENT_SPEED;
        float roll = dy > 0 ? MOVEMENT_SPEED : -MOVEMENT_SPEED;
        float throttle = dz > 0 ? MOVEMENT_SPEED : -MOVEMENT_SPEED;
        float yaw = dr > 0 ? ROTATION_SPEED : -ROTATION_SPEED;

        // 執行移動
        if (dx != 0) move(pitch, 0, 0, 0, moveDuration);
        if (dy != 0) move(0, roll, 0, 0, strafeDuration);
        if (dz != 0) move(0, 0, throttle, 0, verticalDuration);
        if (dr != 0) move(0, 0, 0, yaw, rotationDuration);
    }

    // 無人機起飛
    public void takeoff() {
        if (flightController != null) {
            flightController.startTakeoff(error -> {
                if (error == null) {
                    Log.d("DroneController", "Takeoff successful");
                    enableVirtualStickMode();
                } else {
                    Log.e("DroneController", "Takeoff failed: " + error.getDescription());
                }
            });
        }
    }

    // 無人機降落
    public void land() {
        if (flightController != null) {
            flightController.startLanding(error -> {
                if (error == null) {
                    Log.d("DroneController", "Landing successful");
                } else {
                    Log.e("DroneController", "Landing failed: " + error.getDescription());
                }
            });
        }
    }

    // 控制無人機移動
    private void move(float pitch, float roll, float throttle, float yaw, int duration) {
        if (flightController != null) {
            Runnable moveRunnable = new Runnable() {
                private long startTime = System.currentTimeMillis();

                @Override
                public void run() {
                    long elapsedTime = System.currentTimeMillis() - startTime;

                    if (elapsedTime < duration) {
                        FlightControlData controlData = new FlightControlData(pitch, roll, throttle, yaw);
                        flightController.sendVirtualStickFlightControlData(controlData, null);
                        moveHandler.postDelayed(this, 100); // 每 100 毫秒發送一次
                    } else {
                        // 停止移動
                        FlightControlData stopData = new FlightControlData(0.0f, 0.0f, 0.0f, 0.0f);
                        flightController.sendVirtualStickFlightControlData(stopData, null);
                        Log.d("DroneController", "Movement completed");
                    }
                }
            };

            moveHandler.post(moveRunnable);
        }
    }
}
