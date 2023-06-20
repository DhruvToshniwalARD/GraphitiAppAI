package com.graphiti.app.graphitiappai;

import com.fazecast.jSerialComm.SerialPort;
import javafx.beans.property.SimpleObjectProperty;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GraphitiDriver {
    private SerialPort comPort;
    private static final byte ESC = 0x1B;
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

    public byte[] sendCommand(byte commandID, byte[] commandData) throws IOException {
        if (!this.comPort.isOpen()) {
            throw new IOException("Port is not open");
        }

        // Create a byte list to handle potential duplicate SOF bytes in commandData
        List<Byte> commandList = new ArrayList<>();
        commandList.add(ESC);
        commandList.add(commandID);
        int checksumCount = 2;  // commandID and checksum byte count for checksum calculation
        for (byte data : commandData) {
            commandList.add(data);
            checksumCount++;
            if (data == ESC) { // If data byte equals SOF, add it again
                commandList.add(data);
            }
        }
        // Convert commandList back to array
        byte[] command = new byte[commandList.size() + 1];  // +1 for checksum
        for (int i = 0; i < commandList.size(); i++) {
            command[i] = commandList.get(i);
        }

        // Calculate checksum (ignoring the extra SOF bytes)
        command[command.length - 1] = calculateChecksum(command, 1, checksumCount);

        // If checksum equals SOF, add it again
        if (command[command.length - 1] == ESC) {
            byte[] extendedCommand = Arrays.copyOf(command, command.length + 1);
            extendedCommand[extendedCommand.length - 1] = command[command.length - 1];
            command = extendedCommand;
        }

        // Send command
        this.comPort.writeBytes(command, command.length);

        // Wait for response (assuming response is 2 bytes: ACK/NACK + checksum)
        byte[] response = new byte[2];
        this.comPort.readBytes(response, 2);

        // Validate response
        byte checksum = calculateChecksum(response, 0, 1);
        if (checksum != response[1]) {
            // Handle checksum error
            System.out.println("Checksum Error from Graphiti. Resending the command...");
            return sendCommand(commandID, commandData); // Resend the command if Checksum Error is received
        }

        // Process response according to common responses in VCP mode
        byte responseId = response[0];
        switch (responseId) {
            case 0x53: // Common responses
                switch (response[1]) {
                    case 0x00:
                        System.out.println("Command Successful");
                        break;
                    case 0x01:
                        System.out.println("Command Error. Resending the command...");
                        return sendCommand(commandID, commandData); // Resend the command if Command Error is received
                    case 0x02:
                        System.out.println("Communication Error. Please check the device.");
                        break;
                    case 0x03:
                        System.out.println("Checksum Error from Graphiti. Resending the command...");
                        return sendCommand(commandID, commandData); // Resend the command if Checksum Error is received
                    case 0x04:
                        System.out.println("Invalid Image API Error. Please check the image file and try again.");
                        break;
                    case 0x05:
                        System.out.println("Image API Time Out Error. Resending the command...");
                        return sendCommand(commandID, commandData); // Resend the command if Image API Time Out Error is received
                    default:
                        System.out.println("Unknown error. Response byte: " + response[1]);
                        break;
                }
                break;
            case ACK:
                System.out.println("Received ACK from Graphiti");
                break;
            case NACK:
                System.out.println("Received NACK from Graphiti. Resending the command...");
                return sendCommand(commandID, commandData); // Resend the command if NACK is received
            default:
                System.out.println("Unexpected response. Response byte: " + responseId);
                break;
        }

        return response;
    }

    public byte[] sendImageFile(String imagePath, boolean interruptible) throws IOException {
        byte commandId = interruptible ? (byte) 0x2F : (byte) 0x30; // Command ID for interruptible or blocking send image command

        // Get file name with extension
        File imageFile = new File(imagePath);
        String fileName = imageFile.getName();

        // Read image file into byte array
        byte[] imageData = Files.readAllBytes(imageFile.toPath());

        // Prepare command data: file name + separator + image size + image data
        byte[] commandData = new byte[fileName.length() + 1 + 4 + imageData.length];
        System.arraycopy(fileName.getBytes(), 0, commandData, 0, fileName.length());
        commandData[fileName.length()] = '|';
        ByteBuffer.wrap(commandData, fileName.length() + 1, 4).putInt(imageData.length);
        System.arraycopy(imageData, 0, commandData, fileName.length() + 1 + 4, imageData.length);

        return sendCommand(commandId, commandData);
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
