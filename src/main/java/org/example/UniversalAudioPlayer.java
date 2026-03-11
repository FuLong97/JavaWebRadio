package org.example;

import javafx.application.Platform;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Universal audio player using pure Java Sound SPI.
 * Supports MP3, OGG Vorbis, FLAC — no VLC or external installs needed.
 * Feeds audio data to AudioProcessor for synced visualization.
 */
public class UniversalAudioPlayer {

    private SourceDataLine javaLine;
    private volatile boolean running = false;
    private Thread playerThread;

    private volatile double volume = 0.5;

    private Consumer<String> onStatusChange;
    private Consumer<String> onError;

    private String currentStationName = "";
    private AudioProcessor audioProcessor;

    public void setOnStatusChange(Consumer<String> callback) {
        this.onStatusChange = callback;
    }

    public void setOnError(Consumer<String> callback) {
        this.onError = callback;
    }

    public void setAudioProcessor(AudioProcessor processor) {
        this.audioProcessor = processor;
    }

    public void play(String url, String stationName) {
        stop();
        currentStationName = stationName;
        playWithJavaSound(url);
    }

    public void stop() {
        running = false;
        Thread threadToJoin = playerThread;
        playerThread = null;

        if (javaLine != null) {
            try {
                javaLine.stop();
                javaLine.close();
            } catch (Exception ignored) {}
            javaLine = null;
        }
        if (threadToJoin != null) {
            threadToJoin.interrupt();
            try{
                threadToJoin.join(2000);

            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
        }
    }

    currentStationName = "";

    }

    public void setVolume(double vol) {
        this.volume = Math.max(0.0, Math.min(1.0, vol));
    }

    public double getVolume() {
        return volume;
    }

    public boolean isPlaying() {
        return running;
    }

    public String getCurrentStationName() {
        return currentStationName;
    }

    private void playWithJavaSound(String url) {
        running = true;
        fireStatus("Connecting...");

        playerThread = new Thread(() -> {
            AudioInputStream rawStream = null;
            AudioInputStream decodedStream = null;

            try {
                URL streamUrl = new URL(url);
                BufferedInputStream buffered = new BufferedInputStream(streamUrl.openStream(), 16384);
                rawStream = AudioSystem.getAudioInputStream(buffered);

                AudioFormat sourceFormat = rawStream.getFormat();

                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        sourceFormat.getSampleRate() > 0 ? sourceFormat.getSampleRate() : 44100,
                        16,
                        sourceFormat.getChannels() > 0 ? sourceFormat.getChannels() : 2,
                        sourceFormat.getChannels() > 0 ? sourceFormat.getChannels() * 2 : 4,
                        sourceFormat.getSampleRate() > 0 ? sourceFormat.getSampleRate() : 44100,
                        false
                );

                decodedStream = AudioSystem.getAudioInputStream(decodedFormat, rawStream);

                DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, decodedFormat);
                javaLine = (SourceDataLine) AudioSystem.getLine(lineInfo);
                javaLine.open(decodedFormat);
                javaLine.start();

                fireStatus("Playing");

                byte[] buffer = new byte[4096];
                int bytesRead;

                while (running && (bytesRead = decodedStream.read(buffer, 0, buffer.length)) != -1) {
                    // Feed audio data to visualizer before volume is applied
                    if (audioProcessor != null) {
                        audioProcessor.feedData(buffer, bytesRead);
                    }

                    applyVolume(buffer, bytesRead);
                    javaLine.write(buffer, 0, bytesRead);
                }

                if (running) {
                    fireStatus("Stream ended");
                }

            } catch (UnsupportedAudioFileException e) {
                fireError("Unsupported format: " + e.getMessage());
            } catch (IOException e) {
                if (running) {
                    fireError("Stream error: " + e.getMessage());
                }
            } catch (LineUnavailableException e) {
                fireError("Audio device unavailable: " + e.getMessage());
            } catch (Exception e) {
                fireError("Playback error: " + e.getMessage());
            } finally {
                try { if (decodedStream != null) decodedStream.close(); } catch (Exception ignored) {}
                try { if (rawStream != null) rawStream.close(); } catch (Exception ignored) {}
                if (javaLine != null) {
                    javaLine.drain();
                    javaLine.stop();
                    javaLine.close();
                    javaLine = null;
                }
                running = false;
            }
        });

        playerThread.setDaemon(true);
        playerThread.setName("JavaSound-Player");
        playerThread.start();
    }

    private void applyVolume(byte[] buffer, int bytesRead) {
        if (volume >= 0.99) return;
        for (int i = 0; i < bytesRead - 1; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            sample = (short) (sample * volume);
            buffer[i] = (byte) (sample & 0xFF);
            buffer[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
    }

    private void fireStatus(String status) {
        if (onStatusChange != null) {
            Platform.runLater(() -> onStatusChange.accept(status));
        }
    }

    private void fireError(String error) {
        if (onError != null) {
            Platform.runLater(() -> onError.accept(error));
        }
    }
}