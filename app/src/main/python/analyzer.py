import numpy as np
from scipy import signal
import csv
import os

# Parameters exactly from original script
FS = 13333
NOTCH_FREQ = 60
NOTCH_Q = 50
MOVING_AVG_WIN = int(0.150 * FS)
MICRO_NOISE_FLOOR = 3
THRESHOLD = 25
FREQ_LOW = 20
FREQ_HIGH = 450
MIN_LOG_LENGTH = 5000
END_HOLD_SAMPLES = 800

# STFT Constants from original script
STFT_WIN = 2048
STFT_OVERLAP = 1536

def rms(voltage):
    if len(voltage) == 0: return 0.0
    return np.sqrt(np.mean(voltage**2))

def meanFrequencyFFT(voltage, fs, f_low, f_high):
    N = len(voltage)
    if N < 2: return 0.0
    X = np.fft.rfft(voltage * np.hanning(N))
    Pxx = np.abs(X)**2
    f = np.fft.rfftfreq(N, 1/fs)
    band = (f >= f_low) & (f <= f_high)
    if np.sum(Pxx[band]) == 0: return 0.0
    return np.sum(f[band] * Pxx[band]) / np.sum(Pxx[band])

def movingAverage(voltage, window):
    if len(voltage) == 0: return np.array([])
    return np.convolve(voltage, np.ones(window)/window, mode='same')

def stftMeanFrequency(voltage, fs, f_low, f_high, nperseg, noverlap):
    """Missing function from original script used for TDMF plot."""
    f, t, Zxx = signal.stft(voltage, fs=fs, window='hann', nperseg=nperseg, 
                            noverlap=noverlap, padded=False, boundary=None)
    P = np.abs(Zxx)**2
    band = (f >= f_low) & (f <= f_high)
    f_band = f[band]
    P_band = P[band, :]
    if np.sum(P_band) == 0: return t, np.zeros_like(t)
    mnf = np.sum(f_band[:, None] * P_band, axis=0) / np.sum(P_band, axis=0)
    return t, mnf

def process_ble_data(raw_data_string, csv_path=None):
    try:
        voltage_data = []
        if csv_path and os.path.exists(csv_path):
            with open(csv_path, newline='') as csvfile:
                reader = csv.reader(csvfile)
                next(reader, None)
                for line in reader:
                    try:
                        val = abs(float(line[1])) * 1000 
                        if val < 3000: voltage_data.append(val)
                    except: continue
        
        if not voltage_data and raw_data_string != "INITIAL_LOAD":
            voltage_data = [abs(float(v)) for v in raw_data_string.split(',') if v.strip()]
            
        if not voltage_data:
            return {"vrms": 0.0, "tdmf": 0.0, "is_resting": True, "mov_avg": [], "rectified": [], "stft_mnf": []}

        raw_array = np.array(voltage_data)
        
        # 1. Notch Filter
        b, a = signal.iirnotch(NOTCH_FREQ, NOTCH_Q, FS)
        filtered = signal.filtfilt(b, a, raw_array)
        
        # 2. Rectify and Noise Floor
        rectified = np.abs(filtered - np.mean(filtered))
        rectified[rectified < MICRO_NOISE_FLOOR] = 0
        
        # 3. Moving Average
        mov = movingAverage(rectified, MOVING_AVG_WIN)
        
        # 4. Burst Detection & Metrics
        logging = False
        last_vrms = 0.0
        last_mnf = 0.0
        active_burst = False
        
        burst_start = -1
        below_count = 0
        for i in range(len(mov)):
            if not logging and mov[i] > THRESHOLD:
                logging = True
                burst_start = i
                below_count = 0
            elif logging:
                if mov[i] < THRESHOLD:
                    below_count += 1
                else:
                    below_count = 0
                
                if below_count >= END_HOLD_SAMPLES or i == len(mov) - 1:
                    logging = False
                    end_idx = i - below_count
                    burst_len = end_idx - burst_start
                    if burst_len >= MIN_LOG_LENGTH:
                        burst_sig = filtered[burst_start:end_idx]
                        last_vrms = rms(burst_sig)
                        last_mnf = meanFrequencyFFT(burst_sig, FS, FREQ_LOW, FREQ_HIGH)
                        active_burst = True
        
        # 5. STFT Mean Frequency calculation (TDMF)
        t_stft, mnf_stft = stftMeanFrequency(filtered, FS, FREQ_LOW, FREQ_HIGH, STFT_WIN, STFT_OVERLAP)

        if not active_burst:
            last_vrms = rms(filtered)
            last_mnf = meanFrequencyFFT(filtered, FS, FREQ_LOW, FREQ_HIGH)

        step = max(1, len(mov) // 2000)
        return {
            "vrms": float(last_vrms),
            "tdmf": float(last_mnf),
            "is_resting": not (active_burst or logging),
            "mov_avg": [float(v) for v in mov[::step]],
            "rectified": [float(v) for v in rectified[::step]],
            "stft_mnf": [float(v) for v in mnf_stft] # Returns the time-dependent frequency data
        }

    except Exception as e:
        return {"error": str(e), "vrms": 0.0, "tdmf": 0.0, "is_resting": True, "mov_avg": [], "rectified": []}
