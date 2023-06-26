package com.graphiti.app.graphitiappai;

import com.google.gson.JsonArray;
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
import java.util.HashMap;
import java.util.Map;
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
    private ExecutorService feedbackExecutor;
    private File selectedFile;
    private ExecutorService usbListenerExecutor;
    private GraphitiDriver driver = new GraphitiDriver();
    private JsonObject objectInfo; // JSON object for storing object detection information


    @FXML
    public void initialize() {
        this.imageView = new ImageView();
        listenForUSBConnection();
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

    @FXML
    protected void onUploadButtonClick() {
        FileChooser fileChooser = new FileChooser();
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Image Files", "*.jpeg", "*.jpg", "*.png");
        fileChooser.getExtensionFilters().add(extFilter);
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
                        driver.sendImage(this.selectedFile);
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
        System.out.println("Debug: Sending HTTP request for object detection...");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AppConfig.ENDPOINT + "/vision/v3.0/analyze?visualFeatures=Objects"))
                .header("Content-Type", "application/octet-stream")
                .header("Ocp-Apim-Subscription-Key", AppConfig.SUBSCRIPTION_KEY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(selectedFile.toPath())))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Debug: Object detection response: " + response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    @FXML
    protected void onFeedbackButtonClick() throws IOException, InterruptedException {
        if (this.selectedFile == null) {
            System.out.println("No file selected.");
            return;
        }

        feedbackLabel.setText("");
        detectAndDisplayObjects();
        driver.setTouchEvent(true);

        // Calculate bounding boxes for the downsampled image
        Image image = new Image("file:" + selectedFile.getPath());
        Map<String, JsonObject> downsampledBoundingBoxes = calculateBoundingBoxesForDownsampledImage(image.getWidth(), image.getHeight());

        feedbackExecutor = Executors.newSingleThreadExecutor();
        feedbackExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String touchedObjectName = "";
                    String pinInfo = "";

                    // Keep calling getLastTouchEvent until the response starts with 68
                    do {
                        pinInfo = driver.getPinInfo(driver.getLastTouchEvent());
                    } while (!pinInfo.split(" ")[0].equals("68") || Integer.parseInt(pinInfo.split(" ")[3]) <= 0);

                    String[] pinInfoParts = pinInfo.split(" ");
                    double pinX = Double.parseDouble(pinInfoParts[1]);  // column ID
                    double pinY = Double.parseDouble(pinInfoParts[2]);  // row ID
                    double pinH = Double.parseDouble(pinInfoParts[3]);
                    for (Map.Entry<String, JsonObject> entry : downsampledBoundingBoxes.entrySet()) {
                        JsonObject boundingBox = entry.getValue();

                        double x = boundingBox.get("x").getAsDouble();
                        double y = boundingBox.get("y").getAsDouble();
                        double w = boundingBox.get("w").getAsDouble();
                        double h = boundingBox.get("h").getAsDouble();

                        if (pinX >= x && pinX <= x + w &&
                                pinY >= y && pinY <= y + h) {
                            touchedObjectName = entry.getKey();
                            break;
                        }
                    }

                    final String finalTouchedObjectName = touchedObjectName;
                    Platform.runLater(() -> feedbackLabel.setText(finalTouchedObjectName));

                    Thread.sleep(100); // set delay as per requirement
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }


    private Map<String, JsonObject> calculateBoundingBoxesForDownsampledImage(double originalWidth, double originalHeight) {
        System.out.println("Debug: Original image width and height: " + originalWidth + ", " + originalHeight);

        if (objectInfo == null) {
            System.out.println("Debug: objectInfo is null");
            return new HashMap<>();
        }
        JsonArray objectsArray = objectInfo.getAsJsonArray("objects");
        if (objectsArray == null) {
            System.out.println("Debug: objectsArray is null");
            return new HashMap<>();
        }

        Map<String, JsonObject> boundingBoxes = new HashMap<>();
        double downsampledWidth = 40.0;
        double downsampledHeight = 60.0;
        System.out.println("Debug: Downsampled image width and height: " + downsampledWidth + ", " + downsampledHeight);

        for (JsonElement object : objectsArray) {
            System.out.println("Debug: Processing object: " + object.toString());
            if (object == null || !object.getAsJsonObject().has("rectangle")) {
                System.out.println("Debug: object or its rectangle is null");
                continue;
            }
            JsonObject boundingBox = object.getAsJsonObject().getAsJsonObject("rectangle");

            if (!(boundingBox.has("x") && boundingBox.has("y") && boundingBox.has("w") && boundingBox.has("h"))) {
                System.out.println("Debug: boundingBox does not have all properties");
                continue;
            }

            double x = (boundingBox.get("x").getAsDouble() * downsampledWidth / originalWidth);
            double y = (boundingBox.get("y").getAsDouble() * downsampledHeight / originalHeight);
            double w = boundingBox.get("w").getAsDouble() * downsampledWidth / originalWidth;
            double h = boundingBox.get("h").getAsDouble() * downsampledHeight / originalHeight;


            System.out.println("Debug: Original bounding box (x,y,w,h): " + boundingBox.get("x").getAsDouble() + "," + boundingBox.get("y").getAsDouble() + "," + boundingBox.get("w").getAsDouble() + "," + boundingBox.get("h").getAsDouble());
            System.out.println("Debug: Downsampled bounding box (x,y,w,h): " + x + "," + y + "," + w + "," + h);

            JsonObject downsampledBoundingBox = new JsonObject();
            downsampledBoundingBox.addProperty("x", x);
            downsampledBoundingBox.addProperty("y", y);
            downsampledBoundingBox.addProperty("w", w);
            downsampledBoundingBox.addProperty("h", h);

            boundingBoxes.put(object.getAsJsonObject().get("object").getAsString(), downsampledBoundingBox);
        }

        return boundingBoxes;
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

        objectInfo = JsonParser.parseString(response.body()).getAsJsonObject();

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
        if (feedbackExecutor != null) {
            feedbackExecutor.shutdownNow();
        }
    }
}
