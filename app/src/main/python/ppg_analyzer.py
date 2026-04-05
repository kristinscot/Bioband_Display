import numpy as np
from scipy.signal import find_peaks, butter, filtfilt
import csv
import os

def process_ppg_data(raw_data_string, csv_path):
    """
    Processes PPG data. 
    Reads from the provided csv_path.
    """
    try:
        INPUT_HEIGHT = 1.75 # height of user in meters
        fs = 123.6  # sampling frequency

        # Lists for time and all LED data
        time_ms = []
        green_mv = []
        red_mv = []
        ir_mv = []

        # If the file doesn't exist, we return defaults
        if not csv_path or not os.path.exists(csv_path):
            print(f"File not found: {csv_path}")
            return {
                "points_green": [], "points_red": [], "points_ir": [],
                "bpm": 0, "artStiffness": 0, "breathingRate": 0, "SpO2": 0
            }

        with open(csv_path, newline="") as csvfile:
            reader = csv.DictReader(csvfile)
            for row in reader:
                time_ms.append(float(row["Time_ms"]))
                green_mv.append(float(row["Green_lp"]))
                red_mv.append(float(row["Red_lp"]))
                ir_mv.append(float(row["IR_lp"]))

        # Convert to numpy arrays
        red_mv = np.array(red_mv)
        ir_mv = np.array(ir_mv)
        green_mv = np.array(green_mv)
        t_filtered = np.array(time_ms)

        if len(red_mv) < 10: # Not enough data to process
             return {
                "points_green": green_mv.tolist(), "points_red": red_mv.tolist(), "points_ir": ir_mv.tolist(),
                "bpm": 0, "artStiffness": 0, "breathingRate": 0, "SpO2": 0
            }

        # Remove baseline wander
        nyquist = 0.5 * fs
        b, a = butter(2, 0.2, btype='low', fs=fs)
        baseline = filtfilt(b, a, red_mv)

        # Find heartbeat period peaks
        filtered = red_mv - baseline
        peaks_idx, _ = find_peaks(filtered, prominence=5)
        
        DISPLAY_BPM = 0
        bpms = []
        peaks_time = []

        for i in range(len(peaks_idx)):
            peaks_time.append(t_filtered[peaks_idx[i]])
            if i > 0:
                intervals = (peaks_time[i] - peaks_time[i-1])*10**(-3)
                if intervals > 0:
                    inst_bpm = 60 / intervals
                    bpms.append(inst_bpm)
                    if len(bpms) > 1 and abs(bpms[-1] - bpms[-2]) > 5:
                        DISPLAY_BPM = bpms[-1]
                    elif len(bpms) == 1:
                        DISPLAY_BPM = inst_bpm

        # Find systolic and diastolic peaks for arterial stiffness
        DISPLAY_AS = 0
        filtered_AS = filtered * -1
        peaks_idx_AS, _ = find_peaks(filtered_AS, prominence=0.01)
        peaks_AS_time = []

        for i in range(len(peaks_idx_AS)):
            if filtered_AS[peaks_idx_AS[i]] > 0:
                peaks_AS_time.append(t_filtered[peaks_idx_AS[i]])

        for i in range(len(peaks_AS_time)):
            if i > 0:
                time_diff = peaks_AS_time[i] - peaks_AS_time[i-1]
                if 0 < time_diff < 450:
                    DISPLAY_AS = INPUT_HEIGHT / (time_diff * 0.001)

        # Find breathing rate
        d, c = butter(2, [0.15, 0.4], btype='band', fs=fs)
        breathing_mod = filtfilt(d, c, green_mv)
        peaks_breathing, _ = find_peaks(breathing_mod, prominence=0.01)
        DISPLAY_BR = 0
        for i in range(len(peaks_breathing)):
            if i > 0:
                intervals = (t_filtered[peaks_breathing[i]] - t_filtered[peaks_breathing[i-1]])*10**(-3)
                if intervals > 0:
                    inst_breathing_rate = 60 / intervals
                    if DISPLAY_BR == 0 or abs(inst_breathing_rate - DISPLAY_BR) < 5:
                        DISPLAY_BR = inst_breathing_rate

        # Calculate SpO2
        DISPLAY_SPO2 = 0
        if len(red_mv) > 0 and len(ir_mv) > 0:
            red_ac = np.std(red_mv)
            ir_ac = np.std(ir_mv)
            red_dc = np.mean(red_mv)
            ir_dc = np.mean(ir_mv)

            if red_dc > 0 and ir_dc > 0:
                R = (red_ac/red_dc) / (ir_ac/ir_dc)
                spo2 = 110 - 25 * R
                DISPLAY_SPO2 = np.clip(spo2, 70, 100)

        return {
            "points_green": green_mv.tolist(),
            "points_red": red_mv.tolist(),
            "points_ir": ir_mv.tolist(),
            "bpm": float(DISPLAY_BPM),
            "artStiffness": float(DISPLAY_AS),
            "breathingRate": float(DISPLAY_BR),
            "SpO2": float(DISPLAY_SPO2)
        }

    except Exception as e:
        print(f"Error processing PPG data: {e}")
        return {
            "points_green": [], "points_red": [], "points_ir": [],
            "bpm": 0, "artStiffness": 0, "breathingRate": 0, "SpO2": 0
        }
