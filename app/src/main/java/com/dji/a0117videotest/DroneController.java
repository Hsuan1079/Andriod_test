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

    // 解析指令並控制無人機
    public void processCommand(String command) {
        switch (command) {
            case "takeoff":
                takeoff();
                break;
            case "land":
                land();
                break;
            case "move_forward":
                move(1.0f, 0.0f, 0.0f, 0.0f, 2000); // 向前飛行 2 秒
                break;
            case "move_backward":
                move(-1.0f, 0.0f, 0.0f, 0.0f, 2000);
                break;
            case "move_left":
                move(0.0f, -1.0f, 0.0f, 0.0f, 2000);
                break;
            case "move_right":
                move(0.0f, 1.0f, 0.0f, 0.0f, 2000);
                break;
            case "rotate_left":
                move(0.0f, 0.0f, 0.0f, -30.0f, 2000);
                break;
            case "rotate_right":
                move(0.0f, 0.0f, 0.0f, 30.0f, 2000);
                break;
            default:
                Log.e("DroneController", "Unknown command: " + command);
        }
    }

    // 無人機起飛
    public void takeoff() {
        if (flightController != null) {
            flightController.startTakeoff(error -> {
                if (error == null) {
                    Log.d("DroneController", "Takeoff successful");
                    enableVirtualStickMode(); // 起飛後重新啟用 Virtual Stick
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
    public void move(float pitch, float roll, float throttle, float yaw, int duration) {
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
