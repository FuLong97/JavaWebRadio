package org.example;

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
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * A JavaFX application for a radio player with search, favorites,
 * volume control, and a visualizer.
 */
public class Main extends Application {

    // Path to the file storing favorite stations
    private static final String FAVORITES_FILE = "favorites.txt";

    // VLCJ media player factory and media player instance
    private MediaPlayerFactory mediaPlayerFactory;
    private MediaPlayer mediaPlayer;

    // Lists to hold favorite stations and search results
    private final ObservableList<String> favoriteStations = FXCollections.observableArrayList();
    private final ObservableList<String> searchResults = FXCollections.observableArrayList();

    // UI Components
    private Label nowPlayingLabel = new Label("Now Playing: None");
    private Slider volumeSlider = createStyledSlider();
    private Slider volumeSliderFavorites = createStyledSlider();
    private Label clockLabel = new Label();

    // Canvas for the visualizer
    private final Canvas visualizerCanvas = new Canvas(800, 200);
    private final GraphicsContext gc = visualizerCanvas.getGraphicsContext2D();
    private Timeline visualizerTimeline;

    //API for fetching radio stations
    private RadioBrowserAPI radioAPI = new RadioBrowserAPI();

    // Shared property to synchronize volume sliders across tabs
    private final DoubleProperty sharedVolume = new SimpleDoubleProperty(50);

    @Override
    public void start(Stage primaryStage) {
        // Load favorite stations from file
        loadFavorites();

        // Setup the digital clock
        setupClock();

        // Synchronize sliders with the shared volume property
        volumeSlider.valueProperty().bindBidirectional(sharedVolume);
        volumeSliderFavorites.valueProperty().bindBidirectional(sharedVolume);

        // Update media player volume when shared volume changes
        sharedVolume.addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) {
                mediaPlayer.audio().setVolume(newVal.intValue());
            }
        });

        // Create tabs for Search and Favorites
        VBox searchTabContent = createSearchTab();
        VBox favoritesTabContent = createFavoritesTab();

        // Create a TabPane for navigation
        TabPane tabPane = new TabPane();
        Tab searchTab = new Tab("Search", searchTabContent);
        searchTab.setClosable(false);
        Tab favoritesTab = new Tab("Favorites", favoritesTabContent);
        favoritesTab.setClosable(false);
        tabPane.getTabs().addAll(searchTab, favoritesTab);

        // Main layout using a BorderPane
        BorderPane root = new BorderPane();
        root.setCenter(tabPane);
        root.setBottom(clockLabel);

        // Position and style the clock label
        BorderPane.setAlignment(clockLabel, Pos.BOTTOM_RIGHT);
        BorderPane.setMargin(clockLabel, new Insets(10, 10, 10, 10));

        // Apply a dark theme to the application
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

        // Set up the scene and stage
        Scene scene = new Scene(root, 900, 700);
        primaryStage.setTitle("Radio Player with Search, Favorites, and Visualizer");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> cleanupResources());
        primaryStage.show();

        // Initialize the media player and set its volume
        mediaPlayerFactory = new MediaPlayerFactory("--no-video");
        mediaPlayer = mediaPlayerFactory.mediaPlayers().newMediaPlayer();
        mediaPlayer.audio().setVolume(sharedVolume.intValue());
    }

    /**
     * Creates the Search tab UI layout.
     */
    private VBox createSearchTab() {
        // Search field for entering station names
        TextField searchField = new TextField();
        searchField.setPromptText("Search for Stations...");
        searchField.setStyle("-fx-prompt-text-fill: lightgray;");

        // Buttons for Search, Play, Stop, and Add to Favorites
        Button searchButton = createButtonWithIcon("Search", FontAwesomeSolid.SEARCH, "white", 16);
        ListView<String> searchListView = new ListView<>(searchResults);
        Button playButton = createButtonWithIcon("Play", FontAwesomeSolid.PLAY, "white", 16);
        Button stopButton = createButtonWithIcon("Stop", FontAwesomeSolid.STOP, "white", 16);
        Button addFavoriteButton = createButtonWithIcon("Add to Favorites", FontAwesomeSolid.HEART, "red", 16);

        // Controls box for buttons and volume slider
        HBox controlsBox = new HBox(10, playButton, stopButton, addFavoriteButton, new Label("Volume:"), volumeSlider);
        controlsBox.setAlignment(Pos.CENTER);

        // Event handlers for search and playback actions
        searchButton.setOnAction(e -> {
            String query = searchField.getText().trim();
            if (!query.isEmpty()) {
                searchResults.clear();
                searchResults.addAll(radioAPI.fetchStations(query));
            }
        });
        playButton.setOnAction(e -> {
            String selectedStation = searchListView.getSelectionModel().getSelectedItem();
            if (selectedStation != null) {
                String[] parts = selectedStation.split(" - ");
                if (parts.length == 2) {
                    playStream(parts[1], parts[0]);
                }
            }
        });
        stopButton.setOnAction(e -> stopStream());
        addFavoriteButton.setOnAction(e -> {
            String selectedStation = searchListView.getSelectionModel().getSelectedItem();
            if (selectedStation != null && !favoriteStations.contains(selectedStation)) {
                favoriteStations.add(selectedStation);
                saveFavorites();
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Added to Favorites!", ButtonType.OK);
                alert.showAndWait();
            }
        });

        // Layout for the search tab
        VBox searchTabContent = new VBox(10, searchField, searchButton, searchListView, controlsBox, visualizerCanvas, nowPlayingLabel);
        searchTabContent.setPadding(new Insets(15));
        searchTabContent.setAlignment(Pos.TOP_CENTER);

        return searchTabContent;
    }

    /**
     * Creates the Favorites tab UI layout.
     */
    private VBox createFavoritesTab() {
        // Search field for filtering favorites
        TextField searchField = new TextField();
        searchField.setPromptText("Search Favorites...");
        searchField.setStyle("-fx-prompt-text-fill: lightgray;");

        // Buttons for Play, Stop, and Remove
        ListView<String> favoritesListView = new ListView<>(favoriteStations);
        Button playButton = createButtonWithIcon("Play", FontAwesomeSolid.PLAY, "white", 16);
        Button stopButton = createButtonWithIcon("Stop", FontAwesomeSolid.STOP, "white", 16);
        Button removeButton = createButtonWithIcon("Remove", FontAwesomeSolid.TRASH, "white", 16);

        // Controls box for buttons and volume slider
        HBox controlsBox = new HBox(10, playButton, stopButton, removeButton, new Label("Volume:"), volumeSliderFavorites);
        controlsBox.setAlignment(Pos.CENTER);

        // Event handlers for favorites management and playback
        playButton.setOnAction(e -> {
            String selectedFavorite = favoritesListView.getSelectionModel().getSelectedItem();
            if (selectedFavorite != null) {
                String[] parts = selectedFavorite.split(" - ");
                if (parts.length == 2) {
                    playStream(parts[1], parts[0]);
                }
            }
        });
        stopButton.setOnAction(e -> stopStream());
        removeButton.setOnAction(e -> {
            String selectedFavorite = favoritesListView.getSelectionModel().getSelectedItem();
            if (selectedFavorite != null) {
                favoriteStations.remove(selectedFavorite);
                saveFavorites();
            }
        });

        // Layout for the favorites tab
        VBox favoritesTabContent = new VBox(10, searchField, favoritesListView, controlsBox, nowPlayingLabel);
        favoritesTabContent.setPadding(new Insets(15));
        favoritesTabContent.setAlignment(Pos.TOP_CENTER);

        return favoritesTabContent;
    }

    /**
     * Starts streaming a radio station and updates the visualizer.
     */
    private void playStream(String url, String stationName) {
        stopStream();
        if (mediaPlayer == null) {
            mediaPlayerFactory = new MediaPlayerFactory("--no-video");
            mediaPlayer = mediaPlayerFactory.mediaPlayers().newMediaPlayer();
        }
        mediaPlayer.media().play(url);
        nowPlayingLabel.setText("Now Playing: " + stationName);
        startVisualizer();
    }

    /**
     * Stops the currently playing stream and visualizer.
     */
    private void stopStream() {
        if (mediaPlayer != null && mediaPlayer.status().isPlaying()) {
            mediaPlayer.controls().stop();
            nowPlayingLabel.setText("Now Playing: None");
        }
        if (visualizerTimeline != null) {
            visualizerTimeline.stop();
        }
    }

    /**
     * Starts the visualizer animation on the canvas.
     */
    private void startVisualizer() {
        if (visualizerTimeline != null) {
            visualizerTimeline.stop();
        }

        int totalBars = 50;
        double barWidth = visualizerCanvas.getWidth() / totalBars;

        visualizerTimeline = new Timeline(new KeyFrame(Duration.millis(50), e -> {
            gc.clearRect(0, 0, visualizerCanvas.getWidth(), visualizerCanvas.getHeight());
            double canvasHeight = visualizerCanvas.getHeight();
            for (int i = 0; i < totalBars; i++) {
                double barHeight = Math.random() * canvasHeight;
                double x = i * barWidth;
                double y = canvasHeight - barHeight;
                gc.setFill(javafx.scene.paint.Color.hsb((i * 360.0 / totalBars), 1.0, 1.0));
                gc.fillRect(x, y, barWidth - 2, barHeight);
            }
        }));

        visualizerTimeline.setCycleCount(Timeline.INDEFINITE);
        visualizerTimeline.play();
    }

    /**
     * Sets up a clock that displays the current time.
     */
    private void setupClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            clockLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    /**
     * Loads favorite stations from a file into the favorites list.
     */
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

    /**
     * Saves the current favorites list to a file.
     */
    private void saveFavorites() {
        try {
            Files.write(Paths.get(FAVORITES_FILE), favoriteStations, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error saving favorites: " + e.getMessage());
        }
    }

    /**
     * Creates a styled volume slider.
     */
    private Slider createStyledSlider() {
        Slider slider = new Slider(0, 100, 50);
        slider.setStyle("-fx-base: white; -fx-control-inner-background: white;");
        return slider;
    }

    /**
     * Creates a button with an icon and text.
     */
    private Button createButtonWithIcon(String text, FontAwesomeSolid icon, String color, int size) {
        FontIcon iconView = new FontIcon(icon);
        iconView.setIconColor(javafx.scene.paint.Paint.valueOf(color));
        iconView.setIconSize(size);
        return new Button(text, iconView);
    }

    /**
     * Cleans up resources such as the media player on application exit.
     */
    private void cleanupResources() {
        if (mediaPlayer != null) {
            mediaPlayer.controls().stop();
            mediaPlayer.release();
        }
        if (mediaPlayerFactory != null) {
            mediaPlayerFactory.release();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
