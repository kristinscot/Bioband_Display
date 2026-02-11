import numpy as np
import pandas as pd
from os.path import dirname, join

def process_test_data(raw_data_string):
    """
    Analyzes a string of comma-separated numbers from the Test sensor.
    """
    try:
        numeric_values = [float(val) for val in raw_data_string.split(',') if val]
        data_array = np.array(numeric_values)
        return data_array.tolist()
    except (ValueError, IndexError) as e:
        print(f"Error processing test data: {e}")
        return []

def get_csv_data():
    """
    Translates MATLAB logic to process sample_data.csv:
    Fs = 500
    ecg = a(:,1)
    bp = a(:,3); bp(bp==0) = []; bp = kron(bp, [1;1])
    """
    try:
        csv_path = join(dirname(__file__), "sample_data.csv")
        
        # Load the data. Using pandas to handle potential headers and easy slicing.
        # Assuming the CSV has no header based on MATLAB's readmatrix or has one we skip.
        # Based on previous interaction, we had Time, Value1, Value2.
        # Let's use read_csv with header=None if it's pure data or check for header.
        
        # We'll read it and try to handle both cases.
        df = pd.read_csv(csv_path)
        # If columns are named, we'll convert to numpy array to use indices like MATLAB
        a = df.to_numpy()

        Fs = 500.0
        
        # MATLAB column 1 -> Python index 0 (ECG)
        ecg = a[:, 0]
        
        # MATLAB column 3 -> Python index 2 (Blood Pressure)
        # Handle case where file might only have 2 columns by providing a default
        if a.shape[1] >= 3:
            bp = a[:, 2]
        else:
            # Fallback if column 3 is missing
            bp = np.zeros_like(ecg)

        # bp(bp==0) = []; (Remove zeros)
        bp = bp[bp != 0]
        
        # bp = kron(bp, [1;1]); (Repeat each element twice)
        bp = np.repeat(bp, 2)

        # min_length = min([length(ecg), length(bp)]); 
        min_length = min(len(ecg), len(bp))
        
        # ecg = ecg(1:min_length);
        # bp = bp(1:min_length);
        ecg = ecg[:min_length]
        bp = bp[:min_length]

        # time = (0:min_length-1)/Fs;
        time = np.arange(min_length) / Fs

        # Construct the result as a list of rows for the Android graph:
        # [time, ecg, bp]
        combined = np.column_stack((time, ecg, bp))
        
        return combined.tolist()
        
    except Exception as e:
        print(f"Error reading CSV with MATLAB logic: {e}")
        return []
