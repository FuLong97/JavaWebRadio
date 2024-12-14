package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RadioBrowserAPI {
    private static final String BASE_URL = "https://de1.api.radio-browser.info/json";

    public List<String> fetchStations(String query) {
        List<String> stations = new ArrayList<>();
        try {
            // Build the API URL
            String url = BASE_URL + "/stations/search?name=" + query;

            // Create HTTP Client
            HttpClient client = HttpClient.newHttpClient();

            // Build HTTP Request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            // Send Request and Get Response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Parse JSON Response
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> json = mapper.readValue(response.body(), List.class);

            // Extract Station Name and Stream URL
            for (Map<String, Object> station : json) {
                String name = (String) station.get("name");
                String urlStream = (String) station.get("url");
                if (name != null && urlStream != null) {
                    stations.add(name + " - " + urlStream);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stations;
    }
}
