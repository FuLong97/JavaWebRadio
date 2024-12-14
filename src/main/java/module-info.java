module org.example.demo {
    requires javafx.fxml;
    requires com.fasterxml.jackson.databind; // Für JSON-Serialisierung
    requires java.net.http;
    requires javafx.media;
    requires uk.co.caprica.vlcj;
    requires TarsosDSP.jvm; // Für JVM unterstütztes Audio
    requires static TarsosDSP.core; // TarsosDSP-Kernfunktionen
    requires java.desktop;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires javafx.controls;


    // Öffne explizit nur org.example.demo für Reflection (z. B. JavaFX)
    opens org.example.demo to javafx.fxml;

    // Exportiere das Hauptpaket für andere Sub-Projekte oder Module
    exports org.example;

    // Du kannst explizit `--add-opens` vermeiden, indem du Öffnungen einträgst (falls notwendig)
    // Optional, wenn `--add-opens` nicht als JVM-Option hinzugefügt ist:
    // opens be.tarsos.dsp.mfcc to org.example.demo;
}