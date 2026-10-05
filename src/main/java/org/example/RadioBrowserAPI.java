package org.example;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class RadioBrowserAPI {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile List<String> servers;
    private List<String> servers() throws InterruptedException {
        if (servers != null) return servers;
        Set<String> discovered = new LinkedHashSet<>();
        try {
            for (InetAddress address : InetAddress.getAllByName("all.api.radio-browser.info")) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                String host = address.getCanonicalHostName();
                if (!host.equals(address.getHostAddress())) discovered.add("https://" + host + "/json");
            }
        } catch (IOException ignored) { }
        discovered.addAll(List.of("https://de2.api.radio-browser.info/json", "https://nl1.api.radio-browser.info/json", "https://fi1.api.radio-browser.info/json"));
        List<String> result = new ArrayList<>(discovered);
        Collections.shuffle(result);
        servers = List.copyOf(result);
        return servers;
    }
    public List<Station> fetchStations(String query) throws IOException, InterruptedException {
        String path = "/stations/search?name=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&limit=50&hidebroken=true&order=clickcount&reverse=true";
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        IOException failure = null;
        for (String server : servers()) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(server + path))
                        .timeout(Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(7).toNanos())))
                        .header("User-Agent", "JavaWebRadio/2.1").GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IOException("Server returned " + response.statusCode());
                return parseStations(response.body());
            } catch (IOException e) { failure = e; }
        }
        servers = null;
        throw new IOException("Station search is unavailable. Check your connection and retry.", failure);
    }
    List<Station> parseStations(String json) throws IOException {
        JsonNode data = mapper.readTree(json);
        if (data == null || !data.isArray()) throw new IOException("Invalid station response");
        List<Station> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (JsonNode row : data) {
            String url = row.path("url_resolved").asText("");
            if (url.isBlank()) url = row.path("url").asText("");
            try {
                URI uri = URI.create(url);
                if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) continue;
            } catch (IllegalArgumentException e) { continue; }
            Station station = new Station(row.path("stationuuid").asText(""), row.path("name").asText(""), url,
                    row.path("country").asText(""), row.path("tags").asText(""), row.path("codec").asText(""));
            if (seen.add(station.key())) result.add(station);
        }
        return result;
    }
}
