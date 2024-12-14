package org.example;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;

import javax.sound.sampled.*;
import java.util.Arrays;

public class AudioVisualizer {
    private final AudioDispatcher dispatcher;
    private float smoothedRMS = 0; // Smoothed RMS value for visualizer
    private volatile boolean isProcessing = false;

    public AudioVisualizer() {
        int sampleRate = 44100; // Standard sample rate
        int bufferSize = 1024; // Buffer size
        int bufferOverlap = 512; // Buffer overlap

        dispatcher = createDispatcher(sampleRate, bufferSize, bufferOverlap);

        // Add RMS processor
        dispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                float[] buffer = audioEvent.getFloatBuffer();
                float rms = calculateRMS(buffer);
                smoothedRMS = smoothRMS(smoothedRMS, rms); // Smooth RMS for consistent visuals
                return true;
            }

            @Override
            public void processingFinished() {
                System.out.println("Audio processing finished.");
            }
        });
    }

    public void start() {
        if (isProcessing) return;
        isProcessing = true;
        new Thread(() -> {
            System.out.println("Starting audio processing...");
            dispatcher.run();
        }).start();
    }

    public void stop() {
        dispatcher.stop();
        System.out.println("Audio processing stopped.");
    }

    public float getSmoothedRMS() {
        return smoothedRMS;
    }

    private AudioDispatcher createDispatcher(int sampleRate, int bufferSize, int bufferOverlap) {
        try {
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            AudioInputStream audioStream = new AudioInputStream(line);
            return new AudioDispatcher(new JVMAudioInputStream(audioStream), bufferSize, bufferOverlap);
        } catch (LineUnavailableException e) {
            throw new IllegalStateException("Unable to access audio line. Ensure a microphone is connected.", e);
        }
    }

    private float calculateRMS(float[] buffer) {
        float sum = 0;
        for (float value : buffer) {
            sum += value * value;
        }
        return (float) Math.sqrt(sum / buffer.length);
    }

    private float smoothRMS(float previousRMS, float currentRMS) {
        final float smoothingFactor = 0.8f; // Higher values = smoother response
        return (smoothingFactor * previousRMS) + ((1 - smoothingFactor) * currentRMS);
    }
}
