import numpy as np

def process_ppg_data(raw_data_string):
    """
    Analyzes a string of comma-separated numbers from the PPG sensor.
    For now, it just parses the string. You can add more complex
    filtering or analysis (like peak detection) here later.
    """
    try:
        # Split the string by commas and convert to float
        numeric_values = [float(val) for val in raw_data_string.split(',') if val]
        data_array = np.array(numeric_values)

        # Placeholder for future PPG-specific analysis
        # For example, you might look for heart rate peaks.

        return data_array.tolist()  # Return as a standard Python list

    except (ValueError, IndexError) as e:
        print(f"Error processing PPG data: {e}")
        return [] # Return an empty list on error
