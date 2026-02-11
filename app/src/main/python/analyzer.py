import numpy as np
from scipy.signal import find_peaks, savgol_filter

def process_ble_data(raw_data_string):
    """
    Processes EMG data to extract muscle pulses.
    Returns a dictionary with:
    - 'frequencies': List of frequency values for each detected pulse.
    - 'voltages': List of peak voltage values for each detected pulse.
    - 'ends_in_activation': Boolean, true if the data ends during a muscle activation.
    """
    try:
        # Parse data
        values = [float(v) for v in raw_data_string.split(',') if v.strip()]
        if not values:
            return {"frequencies": [], "voltages": [], "ends_in_activation": False}
            
        data = np.array(values)
        
        # 1. Preprocessing (Rectification & Smoothing)
        # EMG is typically AC-like, so we rectify (absolute value) to find the envelope
        rectified = np.abs(data)
        
        # Smooth to get the envelope. Window length should be adjusted based on sampling rate.
        # Assuming a reasonable sampling rate for EMG (e.g. 100-500Hz).
        window_len = min(len(rectified), 15)
        if window_len % 2 == 0: window_len -= 1
        
        if window_len >= 3:
            envelope = savgol_filter(rectified, window_len, 2)
        else:
            envelope = rectified

        # 2. Pulse Detection (Thresholding on envelope)
        # Using a simple dynamic threshold
        threshold = np.mean(envelope) + 1.0 * np.std(envelope)
        
        # Find peaks in the envelope
        # distance: minimum number of samples between peaks (approx 200ms)
        peaks, _ = find_peaks(envelope, height=threshold, distance=10)
        
        voltages = [float(v) for v in envelope[peaks]]
        
        # Frequency calculation:
        # As a placeholder, we use the zero-crossing rate within a window around the peak
        # to represent the signal frequency during that pulse.
        frequencies = []
        sampling_rate = 100 # Example rate
        for peak in peaks:
            start = max(0, peak - 10)
            end = min(len(data), peak + 10)
            pulse_segment = data[start:end]
            if len(pulse_segment) > 1:
                zero_crossings = np.where(np.diff(np.sign(pulse_segment)))[0]
                freq = len(zero_crossings) * (sampling_rate / (2 * len(pulse_segment)))
                frequencies.append(float(freq))
            else:
                frequencies.append(0.0)

        # 3. Ends in activation?
        # Check if the last sample is still above the threshold
        ends_in_activation = bool(envelope[-1] > threshold)

        return {
            "frequencies": frequencies,
            "voltages": voltages,
            "ends_in_activation": ends_in_activation
        }

    except Exception as e:
        # Fallback for errors
        return {
            "frequencies": [],
            "voltages": [],
            "ends_in_activation": False,
            "error": str(e)
        }
