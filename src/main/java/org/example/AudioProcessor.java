package org.example;

import org.jtransforms.fft.DoubleFFT_1D;
import java.util.Arrays;

/**
 * Performs FFT analysis on audio data for visualization.
 * Supports both raw linear magnitudes and grouped logarithmic bars.
 */
public class AudioProcessor {
    private static final int BUFFER_SIZE = 2048;
    private final double[] fftMagnitudes = new double[BUFFER_SIZE / 2];
    private final double[] smoothedMagnitudes = new double[BUFFER_SIZE / 2];
    private final DoubleFFT_1D fft = new DoubleFFT_1D(BUFFER_SIZE);

    // Adjust SMOOTHING: 0.1 is slow/smooth, 0.7 is fast/reactive.
    private static final double SMOOTHING = 0.4; 
    private static final double SAMPLE_RATE = 44100.0;

    /**
     * Feed raw PCM audio data (16-bit signed, little-endian) for FFT analysis.
     */
    public void feedData(byte[] buffer, int bytesRead) {
        int samples = Math.min(bytesRead / 2, BUFFER_SIZE);
        if (samples < 4) return;

        double[] audioData = new double[BUFFER_SIZE];
        double sum = 0.0;

        // 1. Convert PCM to Normalized Double
        for (int i = 0; i < samples; i++) {
            int pos = i * 2;
            if (pos + 1 >= bytesRead) break;
            int low = buffer[pos] & 0xFF;
            int high = buffer[pos + 1];
            // Interpret as 16-bit signed short then normalize to -1.0 to 1.0
            audioData[i] = (short) ((high << 8) | low) / 32768.0;
            sum += audioData[i];
        }

        // 2. Remove DC Offset (centers the wave at 0)
        double dcOffset = sum / samples;
        for (int i = 0; i < samples; i++) {
            audioData[i] -= dcOffset;
        }

        // 3. Hanning Window (smooths the edges of the sample for cleaner FFT)
        for (int i = 0; i < samples; i++) {
            audioData[i] *= 0.5 * (1 - Math.cos(2 * Math.PI * i / (samples - 1)));
        }

        // 4. Perform FFT
        fft.realForward(audioData);

        // 5. Calculate Magnitudes with Temporal Smoothing
        synchronized (fftMagnitudes) {
            for (int i = 0; i < fftMagnitudes.length; i++) {
                double real = audioData[2 * i];
                double imag = (2 * i + 1 < BUFFER_SIZE) ? audioData[2 * i + 1] : 0;
                
                double magnitude = Math.sqrt(real * real + imag * imag);

                // Smooth the transition so bars don't jitter
                smoothedMagnitudes[i] = (smoothedMagnitudes[i] * SMOOTHING) + (magnitude * (1 - SMOOTHING));
                fftMagnitudes[i] = smoothedMagnitudes[i];
            }
        }
    }


    public double[] getFftMagnitudes() {
        synchronized (fftMagnitudes) {
            return fftMagnitudes.clone();
        }
    }

    /**
     * Groups FFT bins into 'numBars' using a logarithmic scale.
     * This ensures Bass, Mids, and Treble are all represented visually.
     */
public double[] getVisualizerBars(int numBars) {
    double[] bars = new double[numBars];
    double[] currentMags;

    synchronized (fftMagnitudes) {
        currentMags = fftMagnitudes.clone();
    }

    double minFreq = 40;   
    double maxFreq = 15000; 
    
    for (int i = 0; i < numBars; i++) {
        double lowFreq = minFreq * Math.pow(maxFreq / minFreq, (double) i / numBars);
        double highFreq = minFreq * Math.pow(maxFreq / minFreq, (double) (i + 1) / numBars);

        int lowBin = (int) Math.floor(lowFreq / (SAMPLE_RATE / BUFFER_SIZE));
        int highBin = (int) Math.ceil(highFreq / (SAMPLE_RATE / BUFFER_SIZE));
        
        lowBin = Math.max(1, Math.min(lowBin, currentMags.length - 1));
        highBin = Math.max(lowBin + 1, Math.min(highBin, currentMags.length));

        double sum = 0;
        for (int j = lowBin; j < highBin; j++) {
            sum += currentMags[j];
        }
        double avg = sum / (highBin - lowBin);

        
        
 
        double globalGain = 0.1; 

        // 2. Balanced Frequency Boost
        // This ensures the highs move, but don't explode.
        double freqBoost = 1.0 + (Math.pow(i, 1.5) / Math.pow(numBars, 1.5)) * 2.0;
        
        // 3. Logarithmic Compression (The "Bounciness")
        // Math.pow to make the bars more "exponential" so they stay low 
        // until a loud beat hits.
        double val = Math.log10(1 + avg * freqBoost * globalGain) * 1.5;
        val = Math.pow(val, 1.2); // Squashes the mid-range values down
        
        // 4. Final Clamp
        bars[i] = Math.max(0.01, Math.min(1.0, val));
        
        // --- NEW TUNING LOGIC END ---
    }
    return bars;
}

    public void reset() {
        synchronized (fftMagnitudes) {
            Arrays.fill(fftMagnitudes, 0);
            Arrays.fill(smoothedMagnitudes, 0);
        }
    }

    public void start() {}
    public void stop() { reset(); }
}