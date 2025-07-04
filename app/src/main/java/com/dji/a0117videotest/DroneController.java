package com.dji.a0117videotest;

import android.util.Log;
import android.os.Handler;
import dji.sdk.products.Aircraft;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.sdk.base.BaseProduct;
import dji.sdk.base.BaseComponent;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;


public class DroneController {
    private FlightController flightController;
    private final Handler moveHandler = new Handler();
    private static final float MOVEMENT_SPEED = 0.2f;
    private static final float ROTATION_SPEED = 3.0f;

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

    private void enableVirtualStickMode() {
        if (flightController != null) {
            flightController.setVirtualStickModeEnabled(true, error -> {
                if (error == null) {
                    Log.d("DroneController", "✅ Virtual Stick Mode 啟用成功");
                    flightController.setVirtualStickAdvancedModeEnabled(true);

                    flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
                    flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
                    flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
                    flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
                    
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
     * @param dr 旋轉角度 (rad)
     * @param dz 上下距離 (m)
     */
    public void processDistanceCommand(float dx, float dy, float dr, float dz) {
        if (flightController == null) {
            Log.e("DroneController", "FlightController 未初始化");
            return;
        }

        // Calculate movement duration based on distance and speed
        int pDuration = (int) (Math.abs(dx) / MOVEMENT_SPEED * 1000);
        int rDuration = (int) (Math.abs(dy) / MOVEMENT_SPEED * 1000);
        int yDuration = (int) (Math.abs(dr) / ROTATION_SPEED * 1000);
        int tDuration = (int) (Math.abs(dz) / MOVEMENT_SPEED * 1000);
        

        // Calculate direction
        float pitch = dx > 0 ? MOVEMENT_SPEED : -MOVEMENT_SPEED;
        float roll = dy > 0 ? MOVEMENT_SPEED : -MOVEMENT_SPEED;
        float yaw = dr > 0 ? ROTATION_SPEED : -ROTATION_SPEED;
        float throttle = dz > 0 ? MOVEMENT_SPEED : -MOVEMENT_SPEED;

        // Execute movement
        if (dx != 0) move(pitch, 0, 0, 0, pDuration);
        if (dy != 0) move(0, roll, 0, 0, rDuration);
        if (dr != 0) move(0, 0, yaw, 0, yDuration);
        if (dz != 0) move(0, 0, 0, throttle, tDuration);
    }

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

    private void move(float pitch, float roll, float yaw, float throttle, int duration) {
        if (flightController != null) {
            Runnable moveRunnable = new Runnable() {
                private long startTime = System.currentTimeMillis();

                @Override
                public void run() {
                    long elapsedTime = System.currentTimeMillis() - startTime;

                    if (elapsedTime < duration) {
                        FlightControlData controlData = new FlightControlData(pitch, roll, yaw, throttle);
                        flightController.sendVirtualStickFlightControlData(controlData, null);
                        moveHandler.postDelayed(this, 100); // Send every 100ms
                    } else {
                        // Stop movement
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
