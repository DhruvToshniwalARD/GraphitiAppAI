package com.graphiti.app.graphitiappai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Controller {

    @FXML
    private Button uploadButton;
    @FXML
    private Button describeButton;
    @FXML
    private Button feedbackButton;
    @FXML
    private Label connectionStatus;
    @FXML
    private ImageView imageView;
    @FXML
    private Canvas canvas;
    @FXML
    private Label feedbackLabel;

    private File selectedFile;
    private ExecutorService usbListenerExecutor;
    private GraphitiDriver driver = new GraphitiDriver();
    private JsonObject objectInfo; // JSON object for storing object detection information


    @FXML
    public void initialize() {
        this.imageView = new ImageView();
        listenForUSBConnection();
        // this.canvas = new Canvas(500, 500); // Initialize Canvas with arbitrary size
        // checkConnectionStatus();
    }

    private void listenForUSBConnection() {
        usbListenerExecutor = Executors.newSingleThreadExecutor();
        usbListenerExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                // Call the method to check the connection status
                Platform.runLater(this::checkConnectionStatus);
                try {
                    // Check for connection every second
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void checkConnectionStatus() {
        if (driver.isConnected()) {
            connectionStatus.setText("Connected");
            connectionStatus.setTextFill(Color.GREEN);
            //feedbackLabel.setText("");
        } else {
            connectionStatus.setText("Not connected");
            connectionStatus.setTextFill(Color.RED);
            //feedbackLabel.setText("Graphiti device not connected. Can't send image.");
        }
    }

    private void checkPinHover() throws IOException {
        driver.setTouchEvent(true);
        usbListenerExecutor = Executors.newSingleThreadExecutor();
        usbListenerExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (driver.isConnected()) {
                    try {
                        byte[] response = driver.getLastTouchPointStatus();
                        if (response.length > 0) { // if there is some data received from the device
                            // assuming response bytes 2 and 3 represent the row and column ID of the pin
                            int rowId = Byte.toUnsignedInt(response[2]);
                            int columnId = Byte.toUnsignedInt(response[3]);
                            // assuming response byte 4 represents the height of the pin
                            int pinHeight = Byte.toUnsignedInt(response[4]);

                            Platform.runLater(() -> {
                                // update the label with the rowId, columnId, and pinHeight
                                feedbackLabel.setText("Pin Row ID: " + rowId + ", Pin Column ID: " + columnId + ", Pin Height: " + pinHeight);
                            });
                        } else {
                            // clear the label if there is no data received from the device
                            Platform.runLater(() -> feedbackLabel.setText(""));
                        }
                    } catch (IOException e) {
                        System.out.println("An error occurred while getting the last touch point status: " + e.getMessage());
                    }
                }

                try {
                    // Check for pin hover every second
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }


    @FXML
    protected void onUploadButtonClick() {
        FileChooser fileChooser = new FileChooser();
        this.selectedFile = fileChooser.showOpenDialog(null);

        if (this.selectedFile != null) {
            feedbackLabel.setText("Current file: " + selectedFile.getName());
            try {
                URI selectedFileURI = this.selectedFile.toURI();
                Image image = new Image(selectedFileURI.toString());
                this.displayImage(image);

                // Check if Graphiti is connected and send image
                if (driver.isConnected()) {
                    try {
                        //driver.setOrClearDisplay(false);
                        byte[] response = driver.sendImage(this.selectedFile);
                        String responseMessage = driver.processResponse(response);
                        System.out.println(responseMessage);
                    } catch (IOException e) {
                        System.out.println("An error occurred while sending the command: " + e.getMessage());
                    }
                } else {
                    System.out.println("Graphiti device not connected. Can't send image.");
                }

            } catch (IllegalArgumentException e) {
                System.out.println("Invalid image file selected. Please select a valid image file.");
            } catch (Exception e) {
                System.out.println("An unexpected error occurred while uploading the image.");
                e.printStackTrace();
            }
        } else {
            System.out.println("No file selected.");
        }
    }


    private void displayImage(Image image) {
        this.imageView.setImage(image);

        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, this.canvas.getWidth(), this.canvas.getHeight());
        gc.drawImage(image, 0, 0, this.canvas.getWidth(), this.canvas.getHeight());
    }

    @FXML
    protected void onDescribeButtonClick() throws IOException, InterruptedException {
        if (this.selectedFile == null) {
            System.out.println("No file selected.");
            return;
        }

        String description = describeImage();
        objectInfo = detectObjects();

        if (description != null) {
            feedbackLabel.setText("Description: " + description);
        }
    }

    private String describeImage() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AppConfig.ENDPOINT + "/vision/v3.0/analyze?visualFeatures=Description"))
                .header("Content-Type", "application/octet-stream")
                .header("Ocp-Apim-Subscription-Key", AppConfig.SUBSCRIPTION_KEY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(selectedFile.toPath())))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
        String description = jsonObject.getAsJsonObject("description")
                .getAsJsonArray("captions")
                .get(0).getAsJsonObject()
                .get("text").getAsString();

        return description;
    }

    private JsonObject detectObjects() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AppConfig.ENDPOINT + "/vision/v3.0/analyze?visualFeatures=Objects"))
                .header("Content-Type", "application/octet-stream")
                .header("Ocp-Apim-Subscription-Key", AppConfig.SUBSCRIPTION_KEY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(selectedFile.toPath())))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    @FXML
    protected void onFeedbackButtonClick() throws IOException, InterruptedException {
        if (this.selectedFile == null) {
            System.out.println("No file selected.");
            return;
        }
        //checkPinHover();
        feedbackLabel.setText("");
        detectAndDisplayObjects();
    }

    private void detectAndDisplayObjects() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AppConfig.ENDPOINT + "/vision/v3.0/detect"))
                .header("Content-Type", "application/octet-stream")
                .header("Ocp-Apim-Subscription-Key", AppConfig.SUBSCRIPTION_KEY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(selectedFile.toPath())))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonObject objectInfo = JsonParser.parseString(response.body()).getAsJsonObject();

        if (objectInfo != null && objectInfo.has("objects")) {
            Image image = new Image("file:" + selectedFile.getPath());
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.drawImage(image, 0, 0, canvas.getWidth(), canvas.getHeight());

            for (JsonElement object : objectInfo.getAsJsonArray("objects")) {
                JsonObject boundingBox = object.getAsJsonObject().getAsJsonObject("rectangle");

                double x = boundingBox.get("x").getAsDouble() * canvas.getWidth() / image.getWidth();
                double y = boundingBox.get("y").getAsDouble() * canvas.getHeight() / image.getHeight();
                double w = boundingBox.get("w").getAsDouble() * canvas.getWidth() / image.getWidth();
                double h = boundingBox.get("h").getAsDouble() * canvas.getHeight() / image.getHeight();

                gc.setStroke(Color.RED);
                gc.setLineWidth(2);
                gc.strokeRect(x, y, w, h);
            }

            canvas.setOnMouseMoved(event -> {
                String objectName = "";

                for (JsonElement object : objectInfo.getAsJsonArray("objects")) {
                    JsonObject boundingBox = object.getAsJsonObject().getAsJsonObject("rectangle");

                    double x = boundingBox.get("x").getAsDouble() * canvas.getWidth() / image.getWidth();
                    double y = boundingBox.get("y").getAsDouble() * canvas.getHeight() / image.getHeight();
                    double w = boundingBox.get("w").getAsDouble() * canvas.getWidth() / image.getWidth();
                    double h = boundingBox.get("h").getAsDouble() * canvas.getHeight() / image.getHeight();

                    if (event.getX() >= x && event.getX() <= x + w &&
                            event.getY() >= y && event.getY() <= y + h) {
                        objectName = object.getAsJsonObject().get("object").getAsString();
                        break;
                    }
                }

                feedbackLabel.setText(objectName);
            });

            canvas.setOnMouseExited(event -> feedbackLabel.setText(""));
        }
    }


    public void shutdown() {
        if (usbListenerExecutor != null) {
            usbListenerExecutor.shutdownNow();
        }
    }
}
