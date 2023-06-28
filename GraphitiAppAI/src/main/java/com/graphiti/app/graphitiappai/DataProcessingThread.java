package com.graphiti.app.graphitiappai;

import com.fazecast.jSerialComm.SerialPort;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DataProcessingThread extends Thread {
    private SerialPort comPort;
    private boolean zoomEnabled;
    private FileOutputStream outputStream;

    public DataProcessingThread(SerialPort comPort) throws IOException {
        this.comPort = comPort;
        this.zoomEnabled = false;
        String filePath = getDefaultFilePath(); // Generate a default file path
        this.outputStream = new FileOutputStream(filePath);
    }

    public void setZoomEnabled(boolean zoomEnabled) {
        this.zoomEnabled = zoomEnabled;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[10]; // Increase the buffer size to read more bytes

        try {
            while (!Thread.currentThread().isInterrupted()) {
                int bytesRead = comPort.readBytes(buffer, buffer.length);
                if (bytesRead > 0) {
                    byte[] receivedBytes = new byte[bytesRead];
                    System.arraycopy(buffer, 0, receivedBytes, 0, bytesRead);
                    System.out.println("Received bytes: " + byteArrayToString(receivedBytes));

                    // Dump the received bytes to the text file
                    outputStream.write(receivedBytes);

                    if (zoomEnabled) {
                        // Check for the pattern for Dot 5 key
                        byte[] dot5Pattern = {0x1B, 0x32, 0x02, 0x00, 0x00};
                        if (isPatternMatch(receivedBytes, dot5Pattern)) {
                            System.out.println("Zoom");
                            // Perform zoom-related operations here
                        }
                    }

                    // Process other key events or perform operations based on received bytes
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close the output stream when done
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isPatternMatch(byte[] receivedBytes, byte[] pattern) {
        if (receivedBytes.length < pattern.length) {
            return false;
        }

        for (int i = 0; i <= receivedBytes.length - pattern.length; i++) {
            boolean isMatch = true;
            for (int j = 0; j < pattern.length; j++) {
                if (receivedBytes[i + j] != pattern[j]) {
                    isMatch = false;
                    break;
                }
            }
            if (isMatch) {
                return true;
            }
        }

        return false;
    }

    private String byteArrayToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    private String getDefaultFilePath() {
        // Generate a default file path based on the current date and time
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        return "received_bytes_" + timestamp + ".txt";
    }
}
