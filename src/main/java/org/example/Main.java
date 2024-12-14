package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
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

public class Main extends Application {

    private static final String FAVORITES_FILE = "favorites.txt";

    private MediaPlayerFactory mediaPlayerFactory;
    private MediaPlayer mediaPlayer;

    private final ObservableList<String> favoriteStations = FXCollections.observableArrayList();
    private final ObservableList<String> searchResults = FXCollections.observableArrayList();

    private Label nowPlayingLabel = new Label("Now Playing: None");
    private Slider volumeSlider = createStyledSlider();
    private Label clockLabel = new Label();

    private final Canvas visualizerCanvas = new Canvas(800, 200);
    private final GraphicsContext gc = visualizerCanvas.getGraphicsContext2D();
    private Timeline visualizerTimeline;

    private RadioBrowserAPI radioAPI = new RadioBrowserAPI();

    @Override
    public void start(Stage primaryStage) {
        // Load favorites from file
        loadFavorites();

        // Set up clock
        setupClock();

        // Search Tab
        VBox searchTabContent = createSearchTab();

        // Favorites Tab
        VBox favoritesTabContent = createFavoritesTab();

        // TabPane
        TabPane tabPane = new TabPane();
        Tab searchTab = new Tab("Search", searchTabContent);
        searchTab.setClosable(false);

        Tab favoritesTab = new Tab("Favorites", favoritesTabContent);
        favoritesTab.setClosable(false);

        tabPane.getTabs().addAll(searchTab, favoritesTab);

        // Main Scene Layout
        BorderPane root = new BorderPane();
        root.setCenter(tabPane);
        root.setBottom(clockLabel);

        BorderPane.setAlignment(clockLabel, Pos.BOTTOM_RIGHT);
        BorderPane.setMargin(clockLabel, new Insets(10, 10, 10, 10));

        // Apply dark theme
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

        Scene scene = new Scene(root, 900, 700);
        primaryStage.setTitle("Radio Player with Search, Favorites, and Visualizer");
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

        // Add volume control functionality
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) {
                mediaPlayer.audio().setVolume(newVal.intValue());
            }
        });

        HBox controlsBox = new HBox(10, playButton, stopButton, addFavoriteButton, new Label("Volume:"), volumeSlider);
        controlsBox.setAlignment(Pos.CENTER);

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
                saveFavorites(); // Save favorites after adding
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Added to Favorites!", ButtonType.OK);
                alert.showAndWait();
            }
        });

        VBox searchTabContent = new VBox(10, searchField, searchButton, searchListView, controlsBox, visualizerCanvas, nowPlayingLabel);
        searchTabContent.setPadding(new Insets(15));
        searchTabContent.setAlignment(Pos.TOP_CENTER);

        return searchTabContent;
    }

    private VBox createFavoritesTab() {
        TextField searchField = new TextField();
        searchField.setPromptText("Search Favorites...");
        searchField.setStyle("-fx-prompt-text-fill: lightgray;");

        ListView<String> favoritesListView = new ListView<>(favoriteStations);

        Button playButton = createButtonWithIcon("Play", FontAwesomeSolid.PLAY, "white", 16);
        Button stopButton = createButtonWithIcon("Stop", FontAwesomeSolid.STOP, "white", 16);
        Button removeButton = createButtonWithIcon("Remove", FontAwesomeSolid.TRASH, "white", 16);
        Slider volumeSliderFavorites = createStyledSlider();

        // Add volume control functionality
        volumeSliderFavorites.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) {
                mediaPlayer.audio().setVolume(newVal.intValue());
            }
        });

        HBox controlsBox = new HBox(10, playButton, stopButton, removeButton, new Label("Volume:"), volumeSliderFavorites);
        controlsBox.setAlignment(Pos.CENTER);

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
                saveFavorites(); // Save favorites after removal
            }
        });

        VBox favoritesTabContent = new VBox(10, searchField, favoritesListView, controlsBox, nowPlayingLabel);
        favoritesTabContent.setPadding(new Insets(15));
        favoritesTabContent.setAlignment(Pos.TOP_CENTER);

        return favoritesTabContent;
    }

    private void playStream(String url, String stationName) {
        stopStream();

        mediaPlayerFactory = mediaPlayerFactory == null ? new MediaPlayerFactory("--no-video") : mediaPlayerFactory;
        mediaPlayer = mediaPlayerFactory.mediaPlayers().newMediaPlayer();
        mediaPlayer.media().play(url);

        nowPlayingLabel.setText("Now Playing: " + stationName);
        startVisualizer(); // Start the visualizer
    }

    private void stopStream() {
        if (mediaPlayer != null && mediaPlayer.status().isPlaying()) {
            mediaPlayer.controls().stop();
            nowPlayingLabel.setText("Now Playing: None");
        }
        if (visualizerTimeline != null) {
            visualizerTimeline.stop();
        }
    }

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

    private void setupClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            clockLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        }));
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

        Button button = new Button(text, iconView);
        return button;
    }

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
