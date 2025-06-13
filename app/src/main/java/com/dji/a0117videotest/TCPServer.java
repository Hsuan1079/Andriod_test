package com.dji.a0117videotest;

import java.io.*;
import java.net.*;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TCPServer extends Thread {
    private static final int PORT = 8080;
    private static final int TIMEOUT = 30000; // 30 seconds timeout
    private DroneController droneController;
    private MainActivity mainActivity;  // 引入 MainActivity
    private ExecutorService clientThreadPool;
    private volatile boolean isRunning = true;
    private SimpleDateFormat timeFormat;
    private ServerSocket serverSocket;

    public TCPServer(DroneController controller, MainActivity activity) {
        this.droneController = controller;
        this.mainActivity = activity;  // 儲存 MainActivity 參考
        this.clientThreadPool = Executors.newCachedThreadPool();
        this.timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    }

    @Override
    public void run() {
        try {
            // Try to create server socket with reuse address option
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(PORT));
            serverSocket.setSoTimeout(TIMEOUT);
            
            String serverIP = getLocalIPAddress();
            updateUI("=== TCP Server Started ===\n" +
                    "Time: " + getCurrentTime() + "\n" +
                    "IP: " + serverIP + "\n" +
                    "Port: " + PORT + "\n" +
                    "=====================");

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientIP = clientSocket.getInetAddress().getHostAddress();
                    updateUI("New client connected:\n" +
                            "Time: " + getCurrentTime() + "\n" +
                            "Client IP: " + clientIP);
                    
                    clientSocket.setSoTimeout(TIMEOUT);
                    clientThreadPool.execute(() -> handleClient(clientSocket));
                } catch (SocketTimeoutException e) {
                    // Timeout is normal, just continue listening
                    continue;
                } catch (IOException e) {
                    if (isRunning) {  // Only show error if we're still supposed to be running
                        updateUI("Connection error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            updateUI("Server error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleClient(Socket clientSocket) {
        String clientIP = clientSocket.getInetAddress().getHostAddress();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            String command;
            while ((command = reader.readLine()) != null) {
                updateUI("=== Received Command ===\n" +
                        "Time: " + getCurrentTime() + "\n" +
                        "From: " + clientIP + "\n" +
                        "Command: " + command);
                processCommand(command);  // 處理命令
            }
        } catch (IOException e) {
            updateUI("Client connection error from " + clientIP + ":\n" + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                updateUI("Client disconnected:\n" +
                        "Time: " + getCurrentTime() + "\n" +
                        "IP: " + clientIP);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 處理從客戶端接收到的命令
     * 命令格式: "2,Custom,dy,dx,dr,dz"
     */
    private void processCommand(String command) {
        try {
            String[] parts = command.split(",");
            if (parts.length != 6 || !parts[0].equals("2") || !parts[1].equals("Custom")) {
                updateUI("Invalid command format:\n" + command);
                return;
            }

            // 解析距離值
            float dy = Float.parseFloat(parts[2]);  // 左右距離
            float dx = Float.parseFloat(parts[3]);  // 前後距離
            float dr = Float.parseFloat(parts[4]);  // 旋轉角度
            float dz = Float.parseFloat(parts[5]);  // 上下距離

            // 使用無人機控制器處理距離命令
            droneController.processDistanceCommand(dx, dy, dz, dr);
            updateUI("=== Executing Command ===\n" +
                    "Time: " + getCurrentTime() + "\n" +
                    "dx: " + String.format("%.2f", dx) + "\n" +
                    "dy: " + String.format("%.2f", dy) + "\n" +
                    "dz: " + String.format("%.2f", dz) + "\n" +
                    "dr: " + String.format("%.2f", dr));
        } catch (Exception e) {
            updateUI("Error processing command:\n" + e.getMessage());
        }
    }

    private String getCurrentTime() {
        return timeFormat.format(new Date());
    }

    // 取得手機的局域網 IP
    private String getLocalIPAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
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

    private void cleanup() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        clientThreadPool.shutdown();
    }

    public void stopServer() {
        isRunning = false;
        updateUI("=== Server Stopping ===\n" +
                "Time: " + getCurrentTime());
        cleanup();
    }
}
