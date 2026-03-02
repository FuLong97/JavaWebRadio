package org.example;

import org.jtransforms.fft.DoubleFFT_1D;
import java.util.Arrays;

/**
 * Performs FFT analysis on audio data for visualization.
 *
 * Receives PCM audio data directly from UniversalAudioPlayer via feedData().
 * No microphone capture — visualizer is perfectly synced with playback.
 */
public class AudioProcessor {
    private static final int BUFFER_SIZE = 2048;
    private final double[] fftMagnitudes = new double[BUFFER_SIZE / 2];
    private final double[] smoothedMagnitudes = new double[BUFFER_SIZE / 2];
    private final DoubleFFT_1D fft = new DoubleFFT_1D(BUFFER_SIZE);

    private static final double SMOOTHING = 0.1;

    /**
     * Feed raw PCM audio data (16-bit signed, little-endian) for FFT analysis.
     * Called by UniversalAudioPlayer during playback.
     */
    public void feedData(byte[] buffer, int bytesRead) {
        int samples = Math.min(bytesRead / 2, BUFFER_SIZE);
        if (samples < 4) return;

        double[] audioData = new double[BUFFER_SIZE];
        double sum = 0.0;

        // Convert 16-bit little-endian bytes to normalized doubles
        for (int i = 0; i < samples && i < BUFFER_SIZE; i++) {
            int pos = i * 2;
            if (pos + 1 >= bytesRead) break;
            int low = buffer[pos] & 0xFF;
            int high = buffer[pos + 1];
            audioData[i] = (high << 8 | low) / 32768.0;
            sum += audioData[i];
        }

        // Remove DC offset
        double dcOffset = sum / samples;
        for (int i = 0; i < samples; i++) {
            audioData[i] -= dcOffset;
        }

        // Hanning window for cleaner FFT
        for (int i = 0; i < samples; i++) {
            audioData[i] *= 0.5 * (1 - Math.cos(2 * Math.PI * i / (samples - 1)));
        }

        // Perform FFT
        fft.realForward(audioData);

        // Calculate magnitudes with smoothing
        synchronized (fftMagnitudes) {
            for (int i = 0; i < fftMagnitudes.length; i++) {
                double real = audioData[2 * i];
                double imag = (2 * i + 1 < BUFFER_SIZE) ? audioData[2 * i + 1] : 0;
                double magnitude = Math.sqrt(real * real + imag * imag);

                smoothedMagnitudes[i] = smoothedMagnitudes[i] * SMOOTHING + magnitude * (1 - SMOOTHING);
                fftMagnitudes[i] = smoothedMagnitudes[i];
            }
        }
    }

    /**
     * Get the latest FFT magnitudes for visualization.
     */
    public double[] getFftMagnitudes() {
        synchronized (fftMagnitudes) {
            return fftMagnitudes.clone();
        }
    }

    /**
     * Reset magnitudes (called on stop).
     */
    public void reset() {
        synchronized (fftMagnitudes) {
            Arrays.fill(fftMagnitudes, 0);
            Arrays.fill(smoothedMagnitudes, 0);
        }
    }

    public void start() {
        // No-op — data is fed directly via feedData()
    }

    public void stop() {
        reset();
    }
}