package org.example;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for the Radio Browser API.
 * Uses DNS-based server discovery as recommended by the official docs.
 * Automatically discovers live servers and tries each until one responds.
 */
public class RadioBrowserAPI {

    private static final int RESULT_LIMIT = 50;
    private static final int TIMEOUT_SECONDS = 10;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private List<String> serverUrls = null;

    private List<String> discoverServers() {
        List<String> servers = new ArrayList<>();
        try {
            InetAddress[] addresses = InetAddress.getAllByName("all.api.radio-browser.info");
            for (InetAddress addr : addresses) {
                try {
                    String hostname = addr.getCanonicalHostName();
                    if (hostname != null && !hostname.equals(addr.getHostAddress())) {
                        String serverUrl = "https://" + hostname + "/json";
                        if (!servers.contains(serverUrl)) {
                            servers.add(serverUrl);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("DNS discovery failed: " + e.getMessage());
        }

        addIfMissing(servers, "https://de2.api.radio-browser.info/json");
        addIfMissing(servers, "https://nl1.api.radio-browser.info/json");
        addIfMissing(servers, "https://fi1.api.radio-browser.info/json");
        addIfMissing(servers, "https://all.api.radio-browser.info/json");

        Collections.shuffle(servers);
        return servers;
    }

    private void addIfMissing(List<String> servers, String url) {
        if (!servers.contains(url)) servers.add(url);
    }

    private List<String> getServers() {
        if (serverUrls == null) serverUrls = discoverServers();
        return serverUrls;
    }

    private void refreshServers() {
        serverUrls = null;
    }

    public List<String> fetchStations(String query) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String path = "/stations/search?name=" + encodedQuery
                + "&limit=" + RESULT_LIMIT
                + "&hidebroken=true"
                + "&order=clickcount"
                + "&reverse=true";

        List<String> result = tryServers(getServers(), path);
        if (result != null) return result;

        refreshServers();
        result = tryServers(getServers(), path);
        if (result != null) return result;

        System.err.println("All Radio Browser servers failed!");
        return new ArrayList<>();
    }

    private List<String> tryServers(List<String> servers, String path) {
        for (String server : servers) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(server + path))
                        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                        .header("User-Agent", "JavaWebRadio/2.0")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseStations(response.body());
                }
            } catch (Exception e) {
                System.err.println(server + " failed: " + e.getMessage());
            }
        }
        return null;
    }

    private List<String> parseStations(String json) {
        List<String> stations = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = mapper.readValue(json, List.class);

            for (Map<String, Object> station : data) {
                String name = (String) station.get("name");
                String urlStream = (String) station.get("url");
                String codec = (String) station.get("codec");

                if (name != null && urlStream != null && !urlStream.isBlank()) {
                    String entry = name.trim();
                    if (codec != null && !codec.isBlank()) {
                        entry += " [" + codec.toUpperCase() + "]";
                    }
                    entry += " - " + urlStream.trim();
                    stations.add(entry);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing stations: " + e.getMessage());
        }
        return stations;
    }
}