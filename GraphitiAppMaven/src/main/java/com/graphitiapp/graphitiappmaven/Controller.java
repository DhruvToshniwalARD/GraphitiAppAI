package com.graphitiapp.graphitiappmaven;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

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

    private static int imageCount = 0;
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

                // Display the image
                displayImage(image);

                // Reset the state of the application
                resetState();

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

        // Stop the feedback executor if running
        if (feedbackExecutor != null && !feedbackExecutor.isShutdown()) {
            feedbackExecutor.shutdownNow();
        }

        String description = describeImage();
        objectInfo = detectObjects();
        System.out.print("Describe button " + objectInfo);
        if (description != null) {
            feedbackLabel.setText("Description: " + description);
        }

        driver.setKeyEvent(false);
        driver.setTouchEvent(false);

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

    private Stage getCoordinatesStage() {
        Object userData = feedbackButton.getUserData();
        if (userData instanceof Stage) {
            return (Stage) userData;
        } else {
            return null;
        }
    }


    @FXML
    protected void onFeedbackButtonClick() throws IOException, InterruptedException {
        if (this.selectedFile == null) {
            System.out.println("No file selected.");
            return;
        }

        // Check if the coordinates stage is already open
        Stage coordinatesStage = getCoordinatesStage();
        if (coordinatesStage != null) {
            coordinatesStage.requestFocus();
            return;
        }

        Stage primaryStage = (Stage) feedbackButton.getScene().getWindow();

        // Create a new stage for the coordinates window
        coordinatesStage = new Stage();
        coordinatesStage.setTitle("Hovered Coordinates");

        // Set the coordinates stage position and size
        coordinatesStage.setX(primaryStage.getX() + primaryStage.getWidth() / 2);
        coordinatesStage.setY(primaryStage.getY());
        coordinatesStage.setWidth(primaryStage.getWidth() / 2);
        coordinatesStage.setHeight(primaryStage.getHeight());

        GridPane gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        gridPane.setPadding(new Insets(10));

        for (int row = 0; row < 60; row++) {
            for (int col = 0; col < 40; col++) {
                Circle circle = new Circle(4, Color.RED);
                circle.setVisible(false);
                gridPane.add(circle, row, col);
            }
        }

        Scene coordinatesScene = new Scene(gridPane);
        coordinatesStage.setScene(coordinatesScene);
        coordinatesStage.show();

        // Store the coordinates stage in the user data of the feedback button
        feedbackButton.setUserData(coordinatesStage);

        feedbackLabel.setText("");
        detectAndDisplayObjects();
//        driver.setKeyEvent(true);
//        driver.setTouchEvent(true);

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
                    } while (!pinInfo.split(" ")[0].equals("68"));
                    String[] pinInfoParts = pinInfo.split(" ");
                    double pinX = Double.parseDouble(pinInfoParts[2]);  // column ID
                    double pinY = Double.parseDouble(pinInfoParts[1]);  // row ID
                    double pinH = Double.parseDouble(pinInfoParts[3]);

                    // Update the circle's visibility based on the pin coordinates
                    Platform.runLater(() -> {
                        for (Node node : gridPane.getChildren()) {
                            if (GridPane.getColumnIndex(node) == pinX && GridPane.getRowIndex(node) == pinY) {
                                node.setVisible(true);
                                break;
                            }
                        }
                    });

                    for (Map.Entry<String, JsonObject> entry : downsampledBoundingBoxes.entrySet()) {
                        JsonObject boundingBox = entry.getValue();

                        double x = boundingBox.get("x").getAsDouble() + 5;
                        double y = boundingBox.get("y").getAsDouble();
                        double w = boundingBox.get("w").getAsDouble();
                        double h = boundingBox.get("h").getAsDouble();

                        if (pinY >= y && pinY < y + h &&
                                pinX >= x && pinX < x + w) {
                            touchedObjectName = entry.getKey();
//                            System.out.println("Touched Object: " + touchedObjectName);
//                            System.out.println("Graphiti Device - Pin X: " + pinX + ", Pin Y: " + pinY);
//                            System.out.println("Downsampled Bounding Box - X: " + x + ", Y: " + y + ", W: " + w + ", H: " + h);
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
        double targetWidth = 60.0;
        double targetHeight = 40.0;
        System.out.println("Debug: Target image width and height: " + targetWidth + ", " + targetHeight);

        double widthScaleFactor = targetWidth / originalWidth;
        double heightScaleFactor = targetHeight / originalHeight;
        double scaleFactor = Math.min(widthScaleFactor, heightScaleFactor);
        System.out.println("Debug: Scale factor: " + scaleFactor);

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

            double x = boundingBox.get("x").getAsDouble() * scaleFactor;
            double y = boundingBox.get("y").getAsDouble() * scaleFactor;
            double w = boundingBox.get("w").getAsDouble() * scaleFactor;
            double h = boundingBox.get("h").getAsDouble() * scaleFactor;

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
        System.out.print("detect button " + objectInfo);
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

    private void resetState() {
        // Clear feedback label
        feedbackLabel.setText("");

        // Reset object info
        objectInfo = null;

        // Shutdown feedback executor if running
        if (feedbackExecutor != null) {
            feedbackExecutor.shutdownNow();
        }

        // Close coordinates stage if open
        Stage coordinatesStage = getCoordinatesStage();
        if (coordinatesStage != null) {
            coordinatesStage.close();
            feedbackButton.setUserData(null);
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