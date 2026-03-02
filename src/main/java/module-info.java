module org.example.demo {
    requires javafx.controls;
    requires javafx.media;
    requires javafx.fxml;
    requires java.net.http;
    requires java.desktop;
    requires com.fasterxml.jackson.databind;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires JTransforms;

    opens org.example to javafx.fxml;
    exports org.example;
}