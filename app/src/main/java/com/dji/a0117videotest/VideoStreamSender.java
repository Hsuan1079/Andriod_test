package com.dji.a0117videotest;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.camera.VideoFeeder.VideoDataListener;

public class VideoStreamSender {

    private static final String TAG = "VideoStreamSender";
    private String targetIP;
    private int targetPort;
    private DatagramSocket udpSocket;
    private VideoDataListener videoDataListener;

    public VideoStreamSender(String targetIP, int targetPort, MainActivity mainActivity) {
        this.targetIP = targetIP;
        this.targetPort = targetPort;
    }

    public void startStreaming() {
        Log.d(TAG, "Starting UDP Video Stream to " + targetIP + ":" + targetPort);

        try {
            udpSocket = new DatagramSocket();

            // 設定 DJI 影像數據監聽器
            videoDataListener = new VideoDataListener() {
                @Override
                public void onReceive(byte[] videoBuffer, int size) {
                    sendVideoData(videoBuffer, size);
                }
            };

            // 綁定監聽器到 DJI 主影像串流
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(videoDataListener);
            Log.d(TAG, "Video Data Listener added.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start streaming: " + e.getMessage());
        }
    }

    public void stopStreaming() {
        Log.d(TAG, "Stopping UDP Video Stream");

        // 移除影像數據監聽器
        if (videoDataListener != null) {
            VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(videoDataListener);
        }

        // 關閉 UDP Socket
        if (udpSocket != null) {
            udpSocket.close();
            udpSocket = null;
        }
    }

    private void sendVideoData(byte[] data, int length) {
        try {
            InetAddress serverAddress = InetAddress.getByName(targetIP);
            DatagramPacket packet = new DatagramPacket(data, length, serverAddress, targetPort);
            udpSocket.send(packet);
            Log.d(TAG, "Sent " + length + " bytes to " + targetIP + ":" + targetPort);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send video data: " + e.getMessage());
        }
    }
}
