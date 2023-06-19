package com.graphiti.app.graphitiappai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

public class Controller {

    @FXML
    private Button uploadButton;
    @FXML
    private Button describeButton;
    @FXML
    private Button feedbackButton;
    @FXML
    private ImageView imageView;
    @FXML
    private Canvas canvas;
    @FXML
    private Label feedbackLabel;

    private File selectedFile;
    private JsonObject objectInfo; // JSON object for storing object detection information

    @FXML
    public void initialize() {
        // this.imageView = new ImageView();
        // this.canvas = new Canvas(500, 500); // Initialize Canvas with arbitrary size
    }

    @FXML
    protected void onUploadButtonClick() {
        FileChooser fileChooser = new FileChooser();
        this.selectedFile = fileChooser.showOpenDialog(null);

        if (this.selectedFile != null) {
            try {
                URI selectedFileURI = this.selectedFile.toURI();
                Image image = new Image(selectedFileURI.toString());
                this.displayImage(image);
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid image file selected. Please select a valid image file.");
            } catch (Exception e) {
                System.out.println("An unexpected error occurred while uploading the image.");
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
}
