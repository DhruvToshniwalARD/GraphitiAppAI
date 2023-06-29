package com.graphitiapp.graphitiappmaven;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

public class AzureService {

    private static final String ENDPOINT = AppConfig.ENDPOINT;
    private static final String SUBSCRIPTION_KEY = AppConfig.SUBSCRIPTION_KEY;

    public static String describeImage(File selectedFile) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT + "/vision/v3.0/analyze?visualFeatures=Description"))
                .header("Content-Type", "application/octet-stream")
                .header("Ocp-Apim-Subscription-Key", SUBSCRIPTION_KEY)
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

    public static JsonObject detectObjects(File selectedFile) throws IOException, InterruptedException {
        System.out.println("Debug: Sending HTTP request for object detection...");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT + "/vision/v3.0/analyze?visualFeatures=Objects"))
                .header("Content-Type", "application/octet-stream")
                .header("Ocp-Apim-Subscription-Key", SUBSCRIPTION_KEY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(selectedFile.toPath())))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Debug: Object detection response: " + response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
