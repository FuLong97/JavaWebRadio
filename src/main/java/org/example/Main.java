package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.application.*;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class Main extends Application {
    private final UniversalAudioPlayer player = new UniversalAudioPlayer();
    private final AudioProcessor processor = new AudioProcessor();
    private final RadioBrowserAPI api = new RadioBrowserAPI();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObservableList<Station> favorites = FXCollections.observableArrayList();
    private final ObservableList<Station> results = FXCollections.observableArrayList();
    private final ListView<Station> searchList = new ListView<>(results);
    private final FilteredList<Station> filtered = new FilteredList<>(favorites);
    private final ListView<Station> favoritesList = new ListView<>(filtered);
    private final TabPane tabs = new TabPane();
    private final Label title = new Label("Choose a station"), playback = new Label("Stopped"), searchStatus = new Label("Search by station name to start listening.");
    private final Button play = new Button("Play selected"), stop = new Button("Stop"), favorite = new Button("Add favorite"), retry = new Button("Retry station"), searchRetry = new Button("Retry search");
    private final Canvas canvas = new Canvas(800, 90);
    private final ExecutorService searches = Executors.newVirtualThreadPerTaskExecutor();
    private Future<?> searchTask;
    private long searchVersion;
    private boolean closed;
    private Station current;
    private String lastQuery = "";
    private Timeline visualizer;
    private Path favoriteFile;
    private String loadWarning;

    @Override public void start(Stage stage) {
        loadFavorites();
        player.setAudioProcessor(processor);
        player.setOnStatusChange(state -> {
            playback.setText(state);
            boolean playing = state.equals("Playing");
            stop.setDisable(!playing && !state.startsWith("Connecting"));
            retry.setVisible(state.equals("Stream ended"));
            title.setText(current == null ? "Choose a station" : current.name());
            if (!playing) processor.reset();
        });
        player.setOnError(message -> {
            playback.setText(message); stop.setDisable(true); retry.setVisible(true); processor.reset();
        });
        tabs.getTabs().addAll(tab("Discover", searchPane()), tab("Favorites", favoritesPane()));
        tabs.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> updateControls());
        searchList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateControls());
        favoritesList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateControls());
        favorites.addListener((ListChangeListener<Station>) change -> updateControls());
        installCells(searchList); installCells(favoritesList);
        BorderPane root = new BorderPane(tabs);
        root.setTop(header()); root.setBottom(playerBar());
        Scene scene = new Scene(root, 920, 700);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/radio.css")).toExternalForm());
        stage.setTitle("JavaWebRadio 2.1"); stage.setMinWidth(660); stage.setMinHeight(500);
        stage.setScene(scene); stage.setOnCloseRequest(e -> shutdown());
        updateControls(); stage.show();
        if (loadWarning != null) playback.setText(loadWarning);
    }
    private Tab tab(String name, VBox content) { Tab tab = new Tab(name, content); tab.setClosable(false); return tab; }
    private VBox header() {
        Label name = new Label("JavaWebRadio"); name.getStyleClass().add("app-title");
        Label subtitle = new Label("Find your next favorite station."); subtitle.getStyleClass().add("muted");
        VBox header = new VBox(4, name, subtitle); header.setPadding(new Insets(20, 20, 14, 20)); return header;
    }
    private VBox searchPane() {
        TextField field = new TextField(); field.setPromptText("Search stations, e.g. Jazz FM"); field.setMinWidth(0);
        Button search = new Button("Search"); search.getStyleClass().add("primary");
        Runnable submit = () -> { String query = field.getText().trim(); if (!query.isEmpty()) search(query); };
        search.setOnAction(e -> submit.run()); field.setOnAction(e -> submit.run());
        search.disableProperty().bind(field.textProperty().isEmpty());
        HBox row = new HBox(10, field, search); HBox.setHgrow(field, Priority.ALWAYS);
        searchRetry.setOnAction(e -> search(lastQuery)); searchRetry.setVisible(false); searchRetry.managedProperty().bind(searchRetry.visibleProperty());
        HBox status = new HBox(10, searchStatus, searchRetry); status.setAlignment(Pos.CENTER_LEFT);
        searchList.setPlaceholder(new Label("Search for a station above."));
        VBox pane = new VBox(12, row, status, searchList); pane.setPadding(new Insets(16)); VBox.setVgrow(searchList, Priority.ALWAYS); return pane;
    }
    private VBox favoritesPane() {
        TextField filter = new TextField(); filter.setPromptText("Filter favorites by name, country, genre or codec");
        filter.textProperty().addListener((o, a, query) -> {
            filtered.setPredicate(station -> station.matches(query));
            favoritesList.setPlaceholder(new Label(favorites.isEmpty() ? "Save stations with Add favorite." : "No favorites match your filter."));
        });
        favoritesList.setPlaceholder(new Label("Save stations with Add favorite."));
        Button remove = new Button("Remove selected favorite");
        remove.disableProperty().bind(favoritesList.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(e -> {
            Station selected = favoritesList.getSelectionModel().getSelectedItem();
            if (selected != null) { favorites.remove(selected); saveFavorites(); }
        });
        VBox pane = new VBox(12, filter, favoritesList, remove); pane.setPadding(new Insets(16)); VBox.setVgrow(favoritesList, Priority.ALWAYS); return pane;
    }
    private VBox playerBar() {
        title.getStyleClass().add("station-title"); playback.getStyleClass().add("muted");
        title.setMinWidth(0); title.setMaxWidth(Double.MAX_VALUE); title.setWrapText(true); playback.setWrapText(true);
        play.setOnAction(e -> { Station selected = selected(); if (selected != null) playStation(selected); });
        play.getStyleClass().add("primary");
        stop.setDisable(true); stop.setOnAction(e -> {
            player.stop(); processor.reset(); playback.setText("Stopped"); stop.setDisable(true); retry.setVisible(false);
        });
        favorite.setOnAction(e -> {
            Station target = selected() != null ? selected() : current;
            if (target == null) return;
            Station saved = saved(target);
            if (saved != null) favorites.remove(saved); else favorites.add(target);
            saveFavorites(); updateControls();
        });
        retry.setVisible(false); retry.managedProperty().bind(retry.visibleProperty());
        retry.setOnAction(e -> { if (current != null) playStation(current); });
        Slider volume = new Slider(0, 100, 50); volume.setPrefWidth(130);
        Label percent = new Label("50%"); percent.setMinWidth(36);
        volume.valueProperty().addListener((o, a, value) -> { player.setVolume(value.doubleValue() / 100); percent.setText(Math.round(value.doubleValue()) + "%"); });
        FlowPane controls = new FlowPane(10, 10, play, stop, favorite, retry, new Label("Volume"), volume, percent);
        StackPane spectrum = new StackPane(canvas); spectrum.setMinWidth(0); spectrum.setPrefWidth(0); spectrum.setPrefHeight(90);
        canvas.widthProperty().bind(spectrum.widthProperty());
        TitledPane visualization = new TitledPane("Audio visualizer", spectrum); visualization.setExpanded(false);
        visualizer = new Timeline(new KeyFrame(Duration.millis(33), e -> drawSpectrum()));
        visualizer.setCycleCount(Timeline.INDEFINITE); visualizer.play();
        VBox bar = new VBox(8, title, playback, controls, visualization); bar.setPadding(new Insets(16, 20, 16, 20)); bar.getStyleClass().add("player-bar"); bar.setMinWidth(0); return bar;
    }
    private void installCells(ListView<Station> list) {
        list.setMinWidth(0);
        list.setCellFactory(view -> new ListCell<>() {
            final Label name = new Label(), details = new Label();
            final VBox row = new VBox(5, name, details);
            { name.getStyleClass().add("station-title"); details.getStyleClass().add("muted");
              name.setMinWidth(0); details.setMinWidth(0); name.setMaxWidth(Double.MAX_VALUE); details.setMaxWidth(Double.MAX_VALUE); row.setMinWidth(0);
              row.setPadding(new Insets(6)); row.prefWidthProperty().bind(widthProperty().subtract(24));
              name.prefWidthProperty().bind(row.prefWidthProperty()); details.prefWidthProperty().bind(row.prefWidthProperty()); }
            @Override protected void updateItem(Station station, boolean empty) {
                super.updateItem(station, empty); setText(null);
                if (empty || station == null) { setGraphic(null); setTooltip(null); }
                else { name.setText(station.name()); details.setText(station.details().isBlank() ? "Saved station" : station.details());
                    setGraphic(row); setTooltip(new Tooltip(station.name() + "\n" + station.details())); }
            }
        });
        list.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) playStation(list.getSelectionModel().getSelectedItem()); });
        list.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER && list.getSelectionModel().getSelectedItem() != null) { playStation(list.getSelectionModel().getSelectedItem()); e.consume(); } });
        MenuItem copy = new MenuItem("Copy stream URL");
        copy.setOnAction(e -> { Station s = list.getSelectionModel().getSelectedItem(); if (s != null) {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent(); content.putString(s.url()); javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        }});
        copy.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull()); list.setContextMenu(new ContextMenu(copy));
    }
    private Station selected() { return tabs.getSelectionModel().getSelectedIndex() == 1 ? favoritesList.getSelectionModel().getSelectedItem() : searchList.getSelectionModel().getSelectedItem(); }
    private Station saved(Station target) { return favorites.stream().filter(s -> s.key().equals(target.key()) || s.url().equals(target.url())).findFirst().orElse(null); }
    private void updateControls() {
        Station selected = selected(); play.setDisable(selected == null);
        Station target = selected != null ? selected : current;
        favorite.setDisable(target == null); favorite.setText(target != null && saved(target) != null ? "Remove favorite" : "Add favorite");
    }
    private void playStation(Station station) {
        player.stop(); processor.reset(); current = station;
        title.setText(station.name()); playback.setText("Connecting…"); retry.setVisible(false); stop.setDisable(false);
        player.play(station.url(), station.name()); updateControls();
    }
    private void search(String query) {
        if (closed || query.isBlank()) return;
        lastQuery = query; long version = ++searchVersion;
        if (searchTask != null) searchTask.cancel(true);
        results.clear(); searchStatus.setText("Searching…"); searchRetry.setVisible(false);
        searchList.setPlaceholder(new Label("Finding stations…"));
        searchTask = searches.submit(() -> {
            try {
                List<Station> stations = api.fetchStations(query);
                Platform.runLater(() -> {
                    if (closed || version != searchVersion) return;
                    results.setAll(stations); searchStatus.setText(stations.size() + " stations found");
                    searchList.setPlaceholder(new Label("No stations found. Try another name."));
                });
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            catch (Exception error) {
                Platform.runLater(() -> {
                    if (closed || version != searchVersion) return;
                    searchStatus.setText("Search unavailable. Check your connection."); searchRetry.setVisible(true);
                    searchList.setPlaceholder(new Label("Could not load stations. Retry search."));
                });
            }
        });
    }
    private void drawSpectrum() {
        var gc = canvas.getGraphicsContext2D(); gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (!player.isPlaying()) return;
        double[] bars = processor.getVisualizerBars(40); double width = canvas.getWidth() / bars.length;
        for (int i = 0; i < bars.length; i++) {
            gc.setFill(Color.hsb(170 + i * 2, .65, .9)); double height = bars[i] * canvas.getHeight();
            gc.fillRoundRect(i * width, canvas.getHeight() - height, Math.max(1, width - 3), height, 3, 3);
        }
    }
    private void loadFavorites() {
        favoriteFile = Path.of(System.getProperty("user.home"), "JavaWebRadio", "favorites-v2.json");
        try {
            if (Files.exists(favoriteFile)) favorites.setAll(mapper.readValue(Files.readString(favoriteFile), new TypeReference<List<Station>>() {}));
            else if (Files.exists(Path.of("favorites.txt"))) {
                for (String entry : Files.readAllLines(Path.of("favorites.txt"), StandardCharsets.UTF_8)) {
                    Station station = Station.fromLegacy(entry); if (station != null && saved(station) == null) favorites.add(station);
                }
                writeFavorites();
            }
        } catch (IOException e) { loadWarning = "Could not load favorites. Your existing file has been kept."; }
    }
    private void writeFavorites() throws IOException {
        Files.createDirectories(favoriteFile.getParent());
        Path temporary = Files.createTempFile(favoriteFile.getParent(), "favorites-", ".tmp");
        try {
            Files.writeString(temporary, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new ArrayList<>(favorites)));
            try { Files.move(temporary, favoriteFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, favoriteFile, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private void saveFavorites() {
        try { writeFavorites(); } catch (IOException e) { playback.setText("Favorites could not be saved. Check folder permissions."); }
    }
    private void shutdown() {
        closed = true; ++searchVersion; if (searchTask != null) searchTask.cancel(true);
        searches.shutdownNow(); player.stop(); processor.reset(); if (visualizer != null) visualizer.stop();
    }
    @Override public void stop() { shutdown(); }
    public static void main(String[] args) { launch(args); }
}
