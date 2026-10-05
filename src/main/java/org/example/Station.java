package org.example;
import java.util.Locale;
import java.util.stream.*;
public record Station(String id, String name, String url, String country, String tags, String codec) {
    public Station {
        id = clean(id); name = clean(name); url = clean(url);
        country = clean(country); tags = clean(tags); codec = clean(codec);
        if (name.isEmpty()) name = "Unnamed station";
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    public String key() { return id.isEmpty() ? url : id; }
    public String details() {
        return Stream.of(country, tags, codec.toUpperCase(Locale.ROOT)).filter(s -> !s.isBlank()).collect(Collectors.joining(" · "));
    }
    public boolean matches(String query) {
        return (name + " " + country + " " + tags + " " + codec).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).trim());
    }
    public static Station fromLegacy(String entry) {
        int split = entry.lastIndexOf(" - http");
        if (split < 0) return null;
        String name = entry.substring(0, split).trim(), codec = "";
        if (name.endsWith("]") && name.lastIndexOf(" [") >= 0) {
            int start = name.lastIndexOf(" [");
            codec = name.substring(start + 2, name.length() - 1);
            name = name.substring(0, start);
        }
        return new Station("", name, entry.substring(split + 3), "", "", codec);
    }
}
