package org.example;
import javafx.application.Platform;
import javax.sound.sampled.*;
import java.io.*;
import java.net.*;
import java.util.function.Consumer;

/** Playback sessions own their resources. Cancellation never waits on the UI thread. */
public class UniversalAudioPlayer {
    private volatile Session current;
    private volatile double volume = .5;
    private Consumer<String> onStatusChange, onError;
    private AudioProcessor audioProcessor;
    private static final class Session {
        final String name;
        volatile boolean cancelled, playing;
        volatile InputStream input;
        volatile SourceDataLine line;
        Thread thread;
        Session(String name) { this.name = name; }
    }
    public void setOnStatusChange(Consumer<String> callback) { onStatusChange = callback; }
    public void setOnError(Consumer<String> callback) { onError = callback; }
    public void setAudioProcessor(AudioProcessor processor) { audioProcessor = processor; }
    public void setVolume(double value) { volume = Math.max(0, Math.min(1, value)); }
    public double getVolume() { return volume; }
    public boolean isPlaying() { Session s = current; return s != null && s.playing && !s.cancelled; }
    public String getCurrentStationName() { Session s = current; return s == null ? "" : s.name; }
    public synchronized void play(String url, String name) {
        stop();
        Session session = new Session(name); current = session;
        notify(session, "Connecting…", false);
        session.thread = Thread.ofPlatform().daemon().name("Radio-playback").unstarted(() -> decode(session, url));
        session.thread.start();
    }
    public synchronized void stop() {
        Session old = current; current = null;
        if (old == null) return;
        old.cancelled = true; old.playing = false;
        if (old.thread != null) old.thread.interrupt();
        Thread.ofVirtual().name("Radio-cleanup").start(() -> close(old));
    }
    private void notify(Session session, String message, boolean error) {
        Platform.runLater(() -> {
            if (current != session || session.cancelled) return;
            Consumer<String> callback = error ? onError : onStatusChange;
            if (callback != null) callback.accept(message);
        });
    }
    private boolean active(Session session) { return current == session && !session.cancelled; }
    private void decode(Session session, String url) {
        try {
            URLConnection connection = URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(7000);
            connection.setRequestProperty("User-Agent", "JavaWebRadio/2.1");
            session.input = new BufferedInputStream(connection.getInputStream(), 16384);
            if (!active(session)) return;
            try (AudioInputStream raw = AudioSystem.getAudioInputStream(session.input)) {
                AudioFormat source = raw.getFormat();
                int channels = source.getChannels() > 0 ? source.getChannels() : 2;
                float rate = source.getSampleRate() > 0 ? source.getSampleRate() : 44100;
                AudioFormat pcm = new AudioFormat(rate, 16, channels, true, false);
                try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, raw)) {
                    SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, pcm));
                    session.line = line;
                    if (!active(session)) return;
                    line.open(pcm);
                    if (!active(session)) return;
                    line.start(); session.playing = true;
                    notify(session, "Playing", false);
                    byte[] buffer = new byte[4096 - 4096 % pcm.getFrameSize()]; int count;
                    while (active(session) && (count = decoded.read(buffer)) != -1) {
                        if (!active(session)) break;
                        synchronized (this) {
                            if (active(session) && audioProcessor != null) audioProcessor.feedData(buffer, count);
                        }
                        applyVolume(buffer, count); line.write(buffer, 0, count);
                    }
                    if (active(session)) notify(session, "Stream ended", false);
                }
            }
        } catch (UnsupportedAudioFileException | IllegalArgumentException e) {
            if (active(session)) notify(session, "This stream format is not supported. Try another station.", true);
        } catch (LineUnavailableException e) {
            if (active(session)) notify(session, "Audio device unavailable. Check your output device and retry.", true);
        } catch (Exception e) {
            if (active(session)) notify(session, "Connection lost or unavailable. Retry this station.", true);
        } finally { session.playing = false; close(session); }
    }
    private void close(Session session) {
        SourceDataLine line = session.line;
        if (line != null) try { line.stop(); line.flush(); line.close(); } catch (Exception ignored) { }
        InputStream input = session.input;
        if (input != null) try { input.close(); } catch (IOException ignored) { }
    }
    private void applyVolume(byte[] buffer, int count) {
        double level = volume;
        for (int i = 0; i + 1 < count; i += 2) {
            short sample = (short) ((buffer[i] & 255) | (buffer[i + 1] << 8));
            sample = (short) (sample * level);
            buffer[i] = (byte) sample; buffer[i + 1] = (byte) (sample >> 8);
        }
    }
}
