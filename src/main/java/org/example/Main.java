package org.example;

import java.util.List;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Radio Player v2.0
 *
 * - No VLC installation required
 * - Pure Java audio playback (mp3spi, JOrbis, JFlac)
 * - DNS-based server discovery for Radio Browser API
 * - Synced FFT visualizer
 */
public class Main extends Application {

    private static final String FAVORITES_FILE = "favorites.txt";

    private final UniversalAudioPlayer player = new UniversalAudioPlayer();
    private final AudioProcessor audioProcessor = new AudioProcessor();

    private final ObservableList<String> favoriteStations = FXCollections.observableArrayList();
    private final ObservableList<String> searchResults = FXCollections.observableArrayList();

    private final Label nowPlayingLabel = new Label("Now Playing: None");
    private final Label statusLabel = new Label("");
    private final Slider volumeSlider = createStyledSlider();
    private final Slider volumeSliderFavorites = createStyledSlider();
    private final Label clockLabel = new Label();

    private final Canvas visualizerCanvas = new Canvas(800, 200);
    private final GraphicsContext gc = visualizerCanvas.getGraphicsContext2D();
    private Timeline visualizerTimeline;

    private final RadioBrowserAPI radioAPI = new RadioBrowserAPI();
    private final DoubleProperty sharedVolume = new SimpleDoubleProperty(50);

    @Override
    public void start(Stage primaryStage) {
        loadFavorites();
        setupClock();

        // Setup player callbacks
        player.setOnStatusChange(status -> statusLabel.setText(status));
        player.setOnError(error -> statusLabel.setText("Error: " + error));

        // Connect player to audio processor for synced visualization
        player.setAudioProcessor(audioProcessor);

        // Sync volume sliders
        volumeSlider.valueProperty().bindBidirectional(sharedVolume);
        volumeSliderFavorites.valueProperty().bindBidirectional(sharedVolume);

        sharedVolume.addListener((obs, oldVal, newVal) ->
            player.setVolume(newVal.doubleValue() / 100.0)
        );

        // Tabs
        VBox searchTabContent = createSearchTab();
        VBox favoritesTabContent = createFavoritesTab();

        TabPane tabPane = new TabPane();
        Tab searchTab = new Tab("Search", searchTabContent);
        searchTab.setClosable(false);
        Tab favoritesTab = new Tab("Favorites", favoritesTabContent);
        favoritesTab.setClosable(false);
        tabPane.getTabs().addAll(searchTab, favoritesTab);

        // Bottom bar
        HBox bottomBar = new HBox(20, statusLabel, clockLabel);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.setPadding(new Insets(5, 10, 5, 10));

        BorderPane root = new BorderPane();
        root.setCenter(tabPane);
        root.setBottom(bottomBar);

        // Dark theme
        String darkTheme = """
                -fx-background-color: #2B2B2B;
                -fx-control-inner-background: #3C3F41;
                -fx-text-fill: #FFFFFF;
                -fx-base: #2B2B2B;
                -fx-focus-color: #757575;
                -fx-faint-focus-color: #444444;
                """;
        root.setStyle(darkTheme);
        clockLabel.setStyle("-fx-text-fill: white;");
        statusLabel.setStyle("-fx-text-fill: #888888;");

        Scene scene = new Scene(root, 900, 700);
        primaryStage.setTitle("Radio Player v2.0");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> cleanupResources());
        primaryStage.show();
    }

    private VBox createSearchTab() {
        TextField searchField = new TextField();
        searchField.setPromptText("Search for Stations...");
        searchField.setStyle("-fx-prompt-text-fill: lightgray;");

        Button searchButton = createButtonWithIcon("Search", FontAwesomeSolid.SEARCH, "white", 16);
        ListView<String> searchListView = new ListView<>(searchResults);
        Button playButton = createButtonWithIcon("Play", FontAwesomeSolid.PLAY, "white", 16);
        Button stopButton = createButtonWithIcon("Stop", FontAwesomeSolid.STOP, "white", 16);
        Button addFavoriteButton = createButtonWithIcon("Add to Favorites", FontAwesomeSolid.HEART, "red", 16);

        HBox controlsBox = new HBox(10, playButton, stopButton, addFavoriteButton, new Label("Volume:"), volumeSlider);
        controlsBox.setAlignment(Pos.CENTER);

        Runnable doSearch = () -> {
            String query = searchField.getText().trim();
            if (!query.isEmpty()) {
                searchResults.clear();
                statusLabel.setText("Searching...");
                new Thread(() -> {
                    List<String> results = radioAPI.fetchStations(query);
                    Platform.runLater(() -> {
                        searchResults.addAll(results);
                        statusLabel.setText("Found " + results.size() + " stations");
                    });
                }).start();
            }
        };
        searchButton.setOnAction(e -> doSearch.run());
        searchField.setOnAction(e -> doSearch.run());

        playButton.setOnAction(e -> {
            String selected = searchListView.getSelectionModel().getSelectedItem();
            if (selected != null) playStation(selected);
        });

        searchListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = searchListView.getSelectionModel().getSelectedItem();
                if (selected != null) playStation(selected);
            }
        });

        stopButton.setOnAction(e -> stopPlayback());

        addFavoriteButton.setOnAction(e -> {
            String selected = searchListView.getSelectionModel().getSelectedItem();
            if (selected != null && !favoriteStations.contains(selected)) {
                favoriteStations.add(selected);
                saveFavorites();
                statusLabel.setText("Added to favorites!");
            }
        });

        VBox content = new VBox(10, searchField, searchButton, searchListView, controlsBox, visualizerCanvas, nowPlayingLabel);
        content.setPadding(new Insets(15));
        content.setAlignment(Pos.TOP_CENTER);
        return content;
    }

    private VBox createFavoritesTab() {
        TextField searchField = new TextField();
        searchField.setPromptText("Search Favorites...");
        searchField.setStyle("-fx-prompt-text-fill: lightgray;");

        ListView<String> favoritesListView = new ListView<>(favoriteStations);
        Button playButton = createButtonWithIcon("Play", FontAwesomeSolid.PLAY, "white", 16);
        Button stopButton = createButtonWithIcon("Stop", FontAwesomeSolid.STOP, "white", 16);
        Button removeButton = createButtonWithIcon("Remove", FontAwesomeSolid.TRASH, "white", 16);

        HBox controlsBox = new HBox(10, playButton, stopButton, removeButton, new Label("Volume:"), volumeSliderFavorites);
        controlsBox.setAlignment(Pos.CENTER);

        playButton.setOnAction(e -> {
            String selected = favoritesListView.getSelectionModel().getSelectedItem();
            if (selected != null) playStation(selected);
        });

        favoritesListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = favoritesListView.getSelectionModel().getSelectedItem();
                if (selected != null) playStation(selected);
            }
        });

        stopButton.setOnAction(e -> stopPlayback());

        removeButton.setOnAction(e -> {
            String selected = favoritesListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                favoriteStations.remove(selected);
                saveFavorites();
                statusLabel.setText("Removed from favorites");
            }
        });

        VBox content = new VBox(10, searchField, favoritesListView, controlsBox, nowPlayingLabel);
        content.setPadding(new Insets(15));
        content.setAlignment(Pos.TOP_CENTER);
        return content;
    }

    private void playStation(String stationEntry) {
        int urlStart = stationEntry.lastIndexOf(" - http");
        if (urlStart == -1) {
            urlStart = stationEntry.lastIndexOf(" - ");
        }

        if (urlStart > 0) {
            String name = stationEntry.substring(0, urlStart).trim();
            String url = stationEntry.substring(urlStart + 3).trim();

            player.play(url, name);
            nowPlayingLabel.setText("Now Playing: " + name);
            startVisualizer();
        }
    }

    private void stopPlayback() {
        player.stop();
        audioProcessor.reset();
        nowPlayingLabel.setText("Now Playing: None");
        statusLabel.setText("");

        if (visualizerTimeline != null) {
            visualizerTimeline.stop();
        }
        gc.clearRect(0, 0, visualizerCanvas.getWidth(), visualizerCanvas.getHeight());
    }

    private void startVisualizer() {
    if (visualizerTimeline != null) visualizerTimeline.stop();

    // Number of bars in the visualizer
    int totalBars = 40; 
    double spacing = 2;
    double barWidth = (visualizerCanvas.getWidth() / totalBars);

    visualizerTimeline = new Timeline(new KeyFrame(Duration.millis(33), e -> {
        gc.clearRect(0, 0, visualizerCanvas.getWidth(), visualizerCanvas.getHeight());
        double canvasHeight = visualizerCanvas.getHeight();

        // Get the pre-processed bars from the processor
        double[] barMagnitudes = audioProcessor.getVisualizerBars(totalBars);
        
        if (barMagnitudes == null) return;

        for (int i = 0; i < barMagnitudes.length; i++) {
            // The processor returns values between 0.0 and 1.0
            double magnitude = barMagnitudes[i];
            
            // Calculate height based on canvas
            double barHeight = magnitude * canvasHeight;

            // Optional: prevent bars from being completely invisible
            barHeight = Math.max(barHeight, 2); 

            double x = i * barWidth;
            double y = canvasHeight - barHeight;

            // Draw with a nice gradient effect based on frequency (HSB)
            // 0 = Red (Bass), 240 = Blue (Treble)
            double hue = (i * 240.0 / totalBars); 
            gc.setFill(javafx.scene.paint.Color.hsb(hue, 0.8, 0.9));
            
            // Draw the bar (minus spacing for a clean look)
            gc.fillRect(x, y, barWidth - spacing, barHeight);
        }
    }));
    visualizerTimeline.setCycleCount(Timeline.INDEFINITE);
    visualizerTimeline.play();
}

    private void setupClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e ->
            clockLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
        ));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    private void loadFavorites() {
        try {
            File file = new File(FAVORITES_FILE);
            if (file.exists() && file.length() > 0) {
                favoriteStations.addAll(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            System.err.println("Error loading favorites: " + e.getMessage());
        }
    }

    private void saveFavorites() {
        try {
            Files.write(Paths.get(FAVORITES_FILE), favoriteStations, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error saving favorites: " + e.getMessage());
        }
    }

    private Slider createStyledSlider() {
        Slider slider = new Slider(0, 100, 50);
        slider.setStyle("-fx-base: white; -fx-control-inner-background: white;");
        return slider;
    }

    private Button createButtonWithIcon(String text, FontAwesomeSolid icon, String color, int size) {
        FontIcon iconView = new FontIcon(icon);
        iconView.setIconColor(javafx.scene.paint.Paint.valueOf(color));
        iconView.setIconSize(size);
        return new Button(text, iconView);
    }

    private void cleanupResources() {
        player.stop();
        audioProcessor.reset();
    }

    public static void main(String[] args) {
        launch(args);
    }
}