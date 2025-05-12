package com.dji.a0117videotest;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class ControlActivity extends AppCompatActivity {
    private DroneController droneController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        // 取得 DroneController
        droneController = new DroneController();

        // 綁定按鈕
        Button btnTakeoff = findViewById(R.id.btn_takeoff);
        Button btnLand = findViewById(R.id.btn_land);
        Button btnMoveForward = findViewById(R.id.btn_move_forward);
        Button btnMoveBackward = findViewById(R.id.btn_move_backward);
        Button btnMoveLeft = findViewById(R.id.btn_move_left);
        Button btnMoveRight = findViewById(R.id.btn_move_right);
        Button btnRotateLeft = findViewById(R.id.btn_rotate_left);
        Button btnRotateRight = findViewById(R.id.btn_rotate_right);

        // 設定按鈕的點擊事件
        btnTakeoff.setOnClickListener(v -> droneController.processCommand("takeoff"));
        btnLand.setOnClickListener(v -> droneController.processCommand("land"));
        btnMoveForward.setOnClickListener(v -> droneController.processCommand("move_forward"));
        btnMoveBackward.setOnClickListener(v -> droneController.processCommand("move_backward"));
        btnMoveLeft.setOnClickListener(v -> droneController.processCommand("move_left"));
        btnMoveRight.setOnClickListener(v -> droneController.processCommand("move_right"));
        btnRotateLeft.setOnClickListener(v -> droneController.processCommand("rotate_left"));
        btnRotateRight.setOnClickListener(v -> droneController.processCommand("rotate_right"));
    }
}
