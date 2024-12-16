package org.example;

import javax.sound.sampled.*;
import org.jtransforms.fft.DoubleFFT_1D;

public class AudioProcessor {
    private static final int SAMPLE_RATE = 44100; // Standard audio sample rate
    private static final int BUFFER_SIZE = 1024;  // FFT buffer size (must be power of 2)
    private final double  [] fftMagnitudes = new double[BUFFER_SIZE / 2]; // FFT output magnitudes

    /**
     * Start capturing audio and performing FFT.
     */
    public void start() {
        new Thread(() -> {
            try {
                // Audio format: 16-bit PCM, 1 channel (mono), little-endian
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false); // false = little-endian
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);

                // Open and start the audio line
                line.open(format, BUFFER_SIZE);
                line.start();

                byte[] buffer = new byte[BUFFER_SIZE];
                DoubleFFT_1D fft = new DoubleFFT_1D(BUFFER_SIZE);

                while (true) {
                    int bytesRead = line.read(buffer, 0, BUFFER_SIZE);
                    if (bytesRead > 0) {
                        computeFFT(buffer, fft); // Process audio data with FFT
                    }
                }
            } catch (LineUnavailableException e) {
                System.err.println("Error: Audio line unavailable. Ensure your audio format is supported.");
                e.printStackTrace();
            }
        }).start();
    }


    /**
     * Compute FFT on the captured audio buffer and update magnitudes.
     */
    private void computeFFT(byte[] buffer, DoubleFFT_1D fft) {
        double[] audioData = new double[BUFFER_SIZE];
        double sum = 0.0;

        // Convert bytes to doubles and calculate mean (DC offset)
        for (int i = 0; i < BUFFER_SIZE / 2; i++) {
            int low = buffer[2 * i] & 0xFF;
            int high = buffer[2 * i + 1];
            audioData[i] = (high << 8 | low) / 32768.0; // Normalize to range [-1, 1]
            sum += audioData[i];
        }

        // Remove DC offset
        double dcOffset = sum / (BUFFER_SIZE / 2);
        for (int i = 0; i < BUFFER_SIZE / 2; i++) {
            audioData[i] -= dcOffset;
        }

        // Perform FFT
        fft.realForward(audioData);

        // Calculate magnitudes
        for (int i = 0; i < fftMagnitudes.length; i++) {
            double real = audioData[2 * i];
            double imag = audioData[2 * i + 1];
            fftMagnitudes[i] = Math.sqrt(real * real + imag * imag); // Magnitude
        }
    }

    /**
     * Get the latest FFT magnitudes for visualization.
     */
    public double[] getFftMagnitudes() {
        return fftMagnitudes;
    }
}
