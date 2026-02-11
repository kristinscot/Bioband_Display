# Bioband Display - Continuous Health Monitoring

**Bioband Display** is an Android application designed to connect to and visualize real-time data from a custom nRF52840-based bio-sensing device. It serves as the mobile interface for a complete continuous health monitoring system, handling Bluetooth LE communication, advanced Python-based data analysis, and multi-sensor graphical representation.

## What It Does

The app scans for a specific bio-sensing device named `Test Device`, establishes a Bluetooth Low Energy (BLE) connection, and listens for multiplexed data notifications. Upon receiving data (EMG, PPG, Sweat, or Test/ECG), the app uses an integrated Python environment (via Chaquopy) to process it and displays the results as live, auto-scrolling line graphs.

This project demonstrates a full end-to-end medical data pipeline: from hardware sensors, through an embedded microcontroller and BLE, to a mobile application for real-time analysis, storage, and visualization.

## Key Features

-   **Automatic BLE Connection:** Scans for and connects to the nRF52840 device automatically.
-   **Multi-Sensor Support:** Dedicated interfaces for:
    -   **EMG Data:** Real-time muscle activity monitoring.
    -   **PPG Data:** Heart rate and oxygen saturation monitoring.
    -   **Sweat Data:** Conductive sensor analysis.
    -   **ECG/Test Data:** Advanced plotting of multi-column data from sensors or CSV imports.
-   **On-Device Data Processing:** Utilizes a **Python** backend (`NumPy`, `Pandas`, `SciPy`) integrated directly into the app to execute complex analysis logic translated from MATLAB.
-   **CSV Data Management:** 
    -   **Real-time Logging:** Saves incoming EMG data to `emg_data_log.csv` in internal storage for later review.
    -   **Historical Graphing:** Ability to load and visualize multi-column CSV files (e.g., Time, ECG, Blood Pressure) using MATLAB-style processing logic.
-   **Real-time Data Visualization:** Smooth, high-performance rendering using the `MPAndroidChart` library.

## How to Get Started

### Prerequisites

1.  **Android Studio:** Latest stable version.
2.  **Android Device:** A physical phone running Android 7.0 (API 24) up to Android 15 (API 35).
3.  **Python for Build Machine:** Local installation of **Python 3.10** required for the Chaquopy build process.
4.  **Hardware:** The custom nRF52840 "Bioband" device.

### Setup and Installation

1.  **Clone the repository:**
    `git clone https://github.com/kristinscot/Continuous-Health-Monitoring-.git`
2.  **Open in Android Studio:** Select `Open` and navigate to the project folder.
3.  **Sync Project:** Let Android Studio sync Gradle. This will install Python dependencies (`numpy`, `pandas`, `scipy`) automatically.
4.  **Run the App:** Connect your phone and click the **Run** button.

## Architecture

-   **Frontend:** Kotlin (Activities), XML (Layouts), MPAndroidChart (Graphing).
-   **Backend Logic:** Python 3.10 (via Chaquopy).
-   **Communication:** BLE (Bluetooth Low Energy) using a custom Service/Characteristic protocol.
-   **Storage:** Internal App Storage (CSV logging).

## Who We Are

This project is part of a continuous health monitoring research initiative.

-   **Maintainer:** Kristin
