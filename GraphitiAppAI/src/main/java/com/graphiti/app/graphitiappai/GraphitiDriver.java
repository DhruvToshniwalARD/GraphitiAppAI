package com.graphiti.app.graphitiappai;

import com.fazecast.jSerialComm.SerialPort;
import javafx.beans.property.SimpleObjectProperty;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class GraphitiDriver {
    private SerialPort comPort;
    private static final byte ESC = 0x1B; //change name
    private static final byte ACK = 0x51;
    private static final byte NACK = 0x52;
    private static final byte SET_CLEAR_DISPLAY = 0x16;  // Add this line
    private static final byte SET_DISPLAY = 0x02;  // Add this line
    private static final byte CLEAR_DISPLAY = 0x03;

    public boolean isConnected() {
        if (comPort != null && comPort.isOpen()) {
            return true;
        }

        // If the port is null or not open, try to find and open it
        SerialPort[] comPorts = SerialPort.getCommPorts();
        for (SerialPort port : comPorts) {
            if (port.getSystemPortName().equals("COM3")) {
                this.comPort = port;
                boolean opened = this.comPort.openPort();
                if (opened) {
                    this.comPort.setComPortParameters(115200, 8, 1, 0);
                    this.comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                    return true;
                } else {
                    return false;
                }
            }
        }

        // If the port is not found, return false
        return false;
    }


    public byte[] setOrClearDisplay(boolean setDisplay) throws IOException {
        byte[] commandData = new byte[1];
        commandData[0] = setDisplay ? SET_DISPLAY : CLEAR_DISPLAY;
        return sendCommand(SET_CLEAR_DISPLAY, commandData);
    }

    private byte calculateChecksum(byte[] data, int start, int length) {
        int sum = 0;
        for (int i = start; i < start + length; i++) {
            sum += data[i];
        }
        return (byte) ((~sum + 1) & 0xFF);  // Two's complement
    }

    public byte[] updateSinglePixel(int rowId, int columnId, int pixelValue, int blinkingRate) throws IOException {
        byte commandId = 0x17; // Command ID for Update Single Pixel

        byte[] commandData = new byte[4]; // 4 bytes for row ID, column ID, pixel value, blinking rate
        commandData[0] = (byte) rowId;
        commandData[1] = (byte) columnId;
        commandData[2] = (byte) pixelValue;
        commandData[3] = (byte) blinkingRate;

        return sendCommand(commandId, commandData);
    }

    public byte[] sendImage(File imageFile) throws IOException {
        // Use StringBuilder to keep track of data being sent
        StringBuilder sequenceData = new StringBuilder();

        // Get image size
        int imageSize = (int) imageFile.length();

        // Get image name
        String imageName = imageFile.getName();

        /*if (imageName.length() > 255) {
            // Truncate image name or throw an exception
            imageName = imageName.substring(0, 255);
        }*/

        // Create initial command sequence
        byte[] command = new byte[2 + imageName.length() + 1 + 4]; // ESC, command ID, Image name, separator, size
        command[0] = ESC;
        command[1] = 0x30;
        System.arraycopy(imageName.getBytes(), 0, command, 2, imageName.length());
        command[imageName.length() + 2] = '|';

        sequenceData.append(Arrays.toString(command) + "\n"); // Append command to sequenceData

        // Add image size to command
        command[imageName.length() + 3] = (byte) ((imageSize >> 24) & 0xFF);
        command[imageName.length() + 4] = (byte) ((imageSize >> 16) & 0xFF);
        command[imageName.length() + 5] = (byte) ((imageSize >> 8) & 0xFF);
        command[imageName.length() + 6] = (byte) (imageSize & 0xFF);


        // Read image data
        byte[] imageData = Files.readAllBytes(imageFile.toPath());

        sequenceData.append(Arrays.toString(imageData) + "\n"); // Append image data to sequenceData

        // Send command sequence
        this.comPort.writeBytes(command, command.length);

        // Send image data
        this.comPort.writeBytes(imageData, imageData.length);

        Files.write(Paths.get("imagedata.txt"), imageData);

        // Calculate checksum for the command and imageData
        byte[] combinedData = new byte[command.length + imageData.length];
        System.arraycopy(command, 0, combinedData, 0, command.length);
        System.arraycopy(imageData, 0, combinedData, command.length, imageData.length);
        byte checksum = calculateChecksum(combinedData, 1, combinedData.length - 1);

        sequenceData.append("Checksum: " + checksum + "\n"); // Append checksum to sequenceData

        // Send checksum
        byte[] checksumArr = new byte[]{checksum};
        this.comPort.writeBytes(checksumArr, checksumArr.length);

        // Wait for response (assuming response is 4 bytes: SOF, RESPONSE, error code, checksum)
        byte[] response = new byte[4];
        this.comPort.readBytes(response, 4);

        sequenceData.append("Response: " + Arrays.toString(response) + "\n"); // Append response to sequenceData

        processResponse(response);

        // Write the sequenceData to a file
        Files.write(Paths.get("sequenceData.txt"), sequenceData.toString().getBytes());

        return response;
    }


    public byte[] sendCommand(byte commandID, byte[] commandData) throws IOException {

        if (!this.isConnected()) { // Check the connection before sending the command
            throw new IOException("Port is not open");
        }

        // Create command with space for SOF, command ID, command data, checksum
        byte[] command = new byte[2 + commandData.length + 1];
        command[0] = ESC;
        command[1] = commandID;
        System.arraycopy(commandData, 0, command, 2, commandData.length);
        command[command.length - 1] = calculateChecksum(command, 1, commandData.length + 1);  // commandID + commandData

        // Send command
        this.comPort.writeBytes(command, command.length);

        // Wait for response (assuming response is 2 bytes: ACK/NACK + checksum)
        byte[] response = new byte[2];
        this.comPort.readBytes(response, 2);

        // Validate response
        byte checksum = calculateChecksum(response, 0, 1);
        if (checksum != response[1]) {
            // Handle checksum error, e.g., resend command
            throw new IOException("Response checksum error");
        }

        // Check if the response is ACK or NACK
        if (response[0] == ACK) {
            System.out.println("Received ACK from Graphiti");
            // Process response as needed
        } else if (response[0] == NACK) {
            System.out.println("Received NACK from Graphiti. Resending the command...");
            return sendCommand(commandID, commandData); // Resend the command if NACK is received
        }

        return response;
    }

    public byte[] getAllPixelsPositionStatus() throws IOException {
        byte commandID = 0x20; // Command ID for Get All Pixels Position Status
        return sendCommand(commandID, new byte[0]);
    }

    public byte[] setTouchEvent(boolean enable) throws IOException {
        byte commandID = 0x41; // Command ID for Set Touch Event
        byte[] commandData = new byte[1];
        commandData[0] = (byte) (enable ? 0x01 : 0x00); // Set touch event enable or disable
        return sendCommand(commandID, commandData);
    }

    public byte[] getLastTouchPointStatus() throws IOException {
        byte commandID = 0x44; // Command ID for Get Last Touch Point Status
        return sendCommand(commandID, new byte[0]);
    }


    public String processResponse(byte[] response) {
        if (response.length != 4 || response[0] != ESC || response[1] != 0x53) {
            return "Unexpected response format";
        }

        switch (response[2]) {
            case 0x00:
                return "Command Successful";
            case 0x01:
                return "Command Error";
            case 0x02:
                return "Communication Error";
            case 0x03:
                return "Checksum Error";
            case 0x04:
                return "Invalid Image API Error";
            case 0x05:
                return "Image API Time Out Error";
            default:
                return "Unknown response code";
        }
    }


    // Send ACK command to the Graphiti device
    public byte[] sendAck() throws IOException {
        return sendCommand(ACK, new byte[0]);
    }

    // Send NACK command to the Graphiti device
    public byte[] sendNack() throws IOException {
        return sendCommand(NACK, new byte[0]);
    }
}