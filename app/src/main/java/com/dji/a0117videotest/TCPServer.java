package com.dji.a0117videotest;

import java.io.*;
import java.net.*;
import java.util.Enumeration;

public class TCPServer extends Thread {
    private static final int PORT = 8080;
    private DroneController droneController;
    private MainActivity mainActivity;  // 引入 MainActivity

    public TCPServer(DroneController controller, MainActivity activity) {
        this.droneController = controller;
        this.mainActivity = activity;  // 儲存 MainActivity 參考
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            String serverIP = getLocalIPAddress();

            // 伺服器啟動時，顯示 IP & Port
            updateUI("Server started at\nIP: " + serverIP + "\nPort: " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String command = reader.readLine();

                if (command != null) {
                    updateUI("Received command: " + command);
                    droneController.processCommand(command);  // 傳給無人機控制
                }
                clientSocket.close();
            }
        } catch (IOException e) {
            updateUI("Server error: " + e.getMessage());
        }
    }

    // 取得手機的局域網 IP
    private String getLocalIPAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {  // 改用 while 迴圈
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown";
    }


    // 更新 UI，呼叫 MainActivity 的 showStatus()
    private void updateUI(String message) {
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.showStatus(message));
        }
    }
}
