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

public class DroneController {
    private FlightController flightController;
    private final Handler moveHandler = new Handler();
    private static final float MOVEMENT_SPEED = 0.3f;
    private static final float ROTATION_SPEED = 4.0f;

    public DroneController() {
        // Initialize DJI SDK
        DJISDKManager.getInstance().registerApp(null, new DJISDKManager.SDKManagerCallback() {
            @Override
            public void onRegister(DJIError djiError) {
                if (djiError == null) {
                    Log.d("DroneController", "SDK registered successfully");
                    DJISDKManager.getInstance().startConnectionToProduct();
                } else {
                    Log.e("DroneController", "SDK registration failed: " + djiError.getDescription());
                }
            }

            @Override
            public void onProductConnect(BaseProduct baseProduct) {
                if (baseProduct instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft) baseProduct;
                    flightController = aircraft.getFlightController();
                    if (flightController != null) {
                        Log.d("DroneController", "FlightController initialized");
                        enableVirtualStickMode();
                    }
                }
            }

            @Override
            public void onProductDisconnect() {
                Log.d("DroneController", "Product disconnected");
            }

            @Override
            public void onProductChanged(BaseProduct baseProduct) {}

            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent, BaseComponent newComponent) {}

            @Override
            public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int progress) {}

            @Override
            public void onDatabaseDownloadProgress(long current, long total) {}
        });
    }

    private void enableVirtualStickMode() {
        if (flightController != null) {
            boolean isAvailable = flightController.isVirtualStickControlModeAvailable();
            if (!isAvailable) {
                Log.e("DroneController", "❌ Virtual stick control is not available on this aircraft");
                return;
            }

            flightController.setVirtualStickModeEnabled(true, error -> {
                if (error != null) {
                    Log.e("DroneController", "❌ Failed to enable virtual stick mode: " + error.getDescription());
                    return;
                }

                flightController.getVirtualStickModeEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isEnabled) {
                        if (!isEnabled) {
                            Log.e("DroneController", "❌ Virtual stick mode is not enabled");
                            return;
                        }

                        flightController.setVirtualStickAdvancedModeEnabled(true);
                        
                        boolean isAdvancedEnabled = flightController.isVirtualStickAdvancedModeEnabled();
                        if (!isAdvancedEnabled) {
                            Log.e("DroneController", "❌ Advanced mode is not enabled");
                            return;
                        }

                        Log.d("DroneController", "✅ Virtual Stick Mode and Advanced Mode successfully enabled");
                    }

                    @Override
                    public void onFailure(DJIError error) {
                        Log.e("DroneController", "❌ Failed to verify virtual stick mode: " + error.getDescription());
                    }
                });
            });
        } else {
            Log.e("DroneController", "❌ FlightController is null");
        }
    }

    /**
     * 處理距離命令
     * @param dy 前後距離 (m)
     * @param dx 左右距離 (m)
     * @param dz 上下距離 (m)
     * @param dr 旋轉角度 (rad)
     */
    public void processDistanceCommand(float dy, float dx, float dr, float dz) {
        if (flightController == null) {
            Log.e("DroneController", "FlightController 未初始化");
            return;
        }

        // Calculate movement duration based on distance and speed
        int moveDuration = (int) (Math.abs(dy) / MOVEMENT_SPEED * 1000);
        int strafeDuration = (int) (Math.abs(dx) / MOVEMENT_SPEED * 1000);
        int verticalDuration = (int) (Math.abs(dz) / MOVEMENT_SPEED * 1000);
        int rotationDuration = (int) (Math.abs(dr) / ROTATION_SPEED * 1000);

        // Calculate direction
        float pitch = dy > 0 ? MOVEMENT_SPEED : -MOVEMENT_SPEED;
        float roll = dx > 0 ? MOVEMENT_SPEED : -MOVEMENT_SPEED;
        float throttle = dz > 0 ? MOVEMENT_SPEED : -MOVEMENT_SPEED;
        float yaw = dr > 0 ? ROTATION_SPEED : -ROTATION_SPEED;

        // Execute movement
        if (dy != 0) move(pitch, 0, 0, 0, moveDuration);
        if (dx != 0) move(0, roll, 0, 0, strafeDuration);
        if (dr != 0) move(0, 0, yaw, 0, rotationDuration);
        if (dz != 0) move(0, 0, 0, throttle, verticalDuration);
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
