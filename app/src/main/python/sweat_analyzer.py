import numpy as np
import csv
import os

def process_sweat_data(raw_data_string, csv_path=None):
    """
    Processes Sweat/Cortisol data from CSV or raw string.
    Returns three sets of Nyquist data for different plots.
    """
    try:
        results = {}
        
        # 1. Load data from CSV if path provided
        if csv_path and os.path.exists(csv_path):
            concentrations = ["1pg/ml", "10pg/ml", "100pg/ml", "1ng/ml", "10ng/ml", "100ng/ml"]
            # Map concentration to (Z', -Z'') column pairs
            col_map = {
                "1pg/ml": (0, 1),
                "10pg/ml": (3, 4),
                "100pg/ml": (6, 7),
                "1ng/ml": (9, 10),
                "10ng/ml": (12, 13),
                "100ng/ml": (15, 16)
            }
            
            data_by_conc = {conc: {"z_prime": [], "z_double_prime": []} for conc in concentrations}
            
            with open(csv_path, newline='', encoding='utf-8', errors='ignore') as csvfile:
                reader = csv.reader(csvfile)
                next(reader) # Skip header
                for row in reader:
                    for conc, (zp_idx, zdp_idx) in col_map.items():
                        try:
                            if len(row) > max(zp_idx, zdp_idx):
                                zp = float(row[zp_idx])
                                zdp = float(row[zdp_idx])
                                if zp > 0 and zdp > 0: # Filter noise
                                    data_by_conc[conc]["z_prime"].append(zp)
                                    data_by_conc[conc]["z_double_prime"].append(zdp)
                        except: continue

            # Plot 1: Rct from Nyquists (Focus on the region Z' > 6,000,000)
            results["plot1"] = {}
            for conc, d in data_by_conc.items():
                x_filtered = []
                y_filtered = []
                for i in range(len(d["z_prime"])):
                    # Focus on the high-resistance tail shown in the reference image
                    if d["z_prime"][i] > 6000000:
                        x_filtered.append(d["z_prime"][i])
                        y_filtered.append(d["z_double_prime"][i])
                results["plot1"][conc] = {"x": x_filtered, "y": y_filtered}
            
            # Plot 2: Full Nyquists (Show everything)
            results["plot2"] = {conc: {"x": d["z_prime"], "y": d["z_double_prime"]} for conc, d in data_by_conc.items()}
            
            # Plot 3: Filtered (Cut) Nyquists (Middle range)
            results["plot3"] = {}
            for conc, d in data_by_conc.items():
                if len(d["z_prime"]) > 10:
                    results["plot3"][conc] = {
                        "x": d["z_prime"][5:-5],
                        "y": d["z_double_prime"][5:-5]
                    }
                else:
                    results["plot3"][conc] = {"x": d["z_prime"], "y": d["z_double_prime"]}

            return results

        return {"plot1": {}, "plot2": {}, "plot3": {}}

    except Exception as e:
        print(f"Error in sweat_analyzer: {e}")
        return {"plot1": {}, "plot2": {}, "plot3": {}}
