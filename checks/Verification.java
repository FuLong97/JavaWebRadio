package org.example;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.collections.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.net.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class Verification {
    static Main app;
    static Stage stage;
    static <T> T field(String name) throws Exception { Field f=Main.class.getDeclaredField(name); f.setAccessible(true); return (T)f.get(app); }
    static void check(boolean condition,String name) { if(!condition) throw new AssertionError(name); System.out.println("PASS " + name); }
    static void call(String name, Class<?> type,Object value) throws Exception { Method m=Main.class.getDeclaredMethod(name,type); m.setAccessible(true); m.invoke(app,value); }
    static void fx(Callable<Void> action) throws Exception { FutureTask<Void> task=new FutureTask<>(action); Platform.runLater(task); task.get(20,TimeUnit.SECONDS); }
    public static void main(String[] args) throws Exception {
        try {
            Station legacy=Station.fromLegacy("Jazz [MP3] - https://example.com/live");
            check(legacy.name().equals("Jazz") && legacy.codec().equals("MP3"),"legacy favorites parsing");
            RadioBrowserAPI api=new RadioBrowserAPI();
            var parsed=api.parseStations("[{\"name\":\"Jazz\",\"stationuuid\":\"1\",\"url\":\"https://example.com/old\",\"url_resolved\":\"https://example.com/live\",\"country\":\"Austria\",\"tags\":\"jazz\",\"codec\":\"MP3\"},{\"url\":\"file:///etc/test\"}]");
            check(parsed.size()==1 && parsed.get(0).url().endsWith("/live") && parsed.get(0).matches("austria"),"structured API data and stream validation");
            boolean rejected=false; try { api.parseStations("{}"); } catch(IOException e) { rejected=true; }
            check(rejected,"malformed responses produce errors, not empty success");
            Platform.startup(() -> {}); Platform.setImplicitExit(false);
            fx(() -> { app=new Main(); stage=new Stage(); app.start(stage); return null; });
            fx(() -> {
                TabPane tabs=field("tabs"); Label title=field("title"); Button play=field("play");
                check(play.isDisabled(),"play disabled without selection");
                check(title.getScene()!=null && title.getParent().getParent()!=tabs,"player bar exists outside tabs");
                tabs.getSelectionModel().select(1);
                check(title.getScene()!=null,"player remains attached on Favorites tab");
                ObservableList<Station> favorites=field("favorites");
                check(!favorites.isEmpty(),"existing favorites migrate on startup");
                favorites.setAll(new Station("1","Jazz","https://example.com/jazz","Austria","jazz","MP3"),new Station("2","Rock","https://example.com/rock","Germany","rock","MP3"));
                javafx.scene.layout.VBox pane=(javafx.scene.layout.VBox)tabs.getTabs().get(1).getContent();
                ((TextField)pane.getChildren().get(0)).setText("austria");
                ListView<Station> list=field("favoritesList"); check(list.getItems().size()==1 && list.getItems().get(0).name().equals("Jazz"),"favorites input filters by metadata");
                list.getSelectionModel().select(0); check(!play.isDisabled(),"play enables for selected station");
                Method persist=Main.class.getDeclaredMethod("writeFavorites");persist.setAccessible(true);persist.invoke(app); Path file=field("favoriteFile");
                check(Files.exists(file),"migrated favorites persisted"); var saved=new com.fasterxml.jackson.databind.ObjectMapper().readValue(Files.readString(file),new com.fasterxml.jackson.core.type.TypeReference<List<Station>>(){});check(saved.size()==2 && saved.get(0).country().equals("Austria"),"favorites metadata survives JSON round-trip");
                return null;
            });
            CountDownLatch oldStarted=new CountDownLatch(1),releaseOld=new CountDownLatch(1);
            RadioBrowserAPI delayed=new RadioBrowserAPI() {
                @Override public List<Station> fetchStations(String query) throws IOException {
                    if(query.equals("old")) { oldStarted.countDown(); boolean waiting=true; while(waiting) try {releaseOld.await();waiting=false;}catch(InterruptedException ignored){} }
                    if(query.equals("fail")) throw new IOException("offline");
                    return List.of(new Station(query,query,"https://example.com/live","","","MP3"));
                }
            };
            fx(() -> {Field f=Main.class.getDeclaredField("api");f.setAccessible(true);f.set(app,delayed);call("search",String.class,"old");return null;});
            check(oldStarted.await(3,TimeUnit.SECONDS),"first search started");
            fx(() -> {call("search",String.class,"new");return null;});
            Thread.sleep(200); releaseOld.countDown(); Thread.sleep(200);
            fx(() -> { ObservableList<Station> results=field("results");check(results.size()==1 && results.get(0).name().equals("new"),"late search cannot overwrite newer results");call("search",String.class,"fail");return null;});
            Thread.sleep(200);
            fx(() -> {Button retry=field("searchRetry");Label status=field("searchStatus");check(retry.isVisible() && status.getText().contains("unavailable"),"failed search exposes Retry");return null;});
            try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
                CountDownLatch accepted=new CountDownLatch(1);
                Thread.ofVirtual().start(() -> {try(Socket socket=server.accept()){accepted.countDown();Thread.sleep(1500);}catch(Exception ignored){}});
                UniversalAudioPlayer player=new UniversalAudioPlayer();
                player.play("http://127.0.0.1:"+server.getLocalPort()+"/slow","Slow station");
                check(accepted.await(3,TimeUnit.SECONDS),"slow stream connected");
                long before=System.nanoTime(); player.stop();
                check(System.nanoTime()-before < TimeUnit.MILLISECONDS.toNanos(250),"stop returns without waiting for blocked network read");
                check(!player.isPlaying() && player.getCurrentStationName().isEmpty(),"cancelled session no longer current");
            }
            fx(() -> {
                TabPane tabs=field("tabs");tabs.getSelectionModel().select(0);
                ObservableList<Station> results=field("results");results.setAll(parsed.get(0),new Station("2","Ambient Night Radio","https://example.com/ambient","Germany","ambient, electronic","OGG"));
                ((Label)field("searchStatus")).setText("2 stations found");((Button)field("searchRetry")).setVisible(false);
                ListView<Station> list=field("searchList");list.getSelectionModel().select(0);
                stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
                WritableImage image=stage.getScene().snapshot(null); BufferedImage output=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                ImageIO.write(output,"png",Path.of("updated-interface.png").toFile());
                stage.setWidth(660);stage.setHeight(500);stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
                return null; }); Thread.sleep(300); fx(() -> { check(stage.getScene().getRoot().getLayoutBounds().getWidth()<=660,"layout fits minimum window width");
                app.stop();stage.close();return null;
            });
            Platform.exit();System.out.println("ALL CHECKS PASSED");System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();Platform.exit();System.exit(1);}
    }
}
