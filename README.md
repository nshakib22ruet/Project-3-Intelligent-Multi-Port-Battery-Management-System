# ESP32-Based Intelligent Multi-Port Battery Management System

An ESP32-based battery management system designed to monitor and control multiple battery charging ports. The project combines real-time voltage, current, and temperature monitoring with automated charging protection and regression-based battery health assessment.

**Live Project:** [Visit the Project Website](https://nshakib22ruet.github.io/Project-3-Intelligent-Multi-Port-Battery-Management-System/)

**Source Code:** [GitHub Repository](https://github.com/nshakib22ruet/Project-3-Intelligent-Multi-Port-Battery-Management-System)

## Overview

The system uses a step-down transformer, bridge rectifier, filter capacitor, and voltage regulators for power management. An ESP32 acts as the central controller, collecting data from INA219 voltage/current sensors and DS18B20 temperature sensors.

A 4-channel relay module controls the charging paths, while an LM2596 buck converter regulates the charging voltage for Port 2. Users can monitor battery conditions and charging activity through an interactive web dashboard.

## Key Features

- Real-time voltage, current, and temperature monitoring.
- Multi-port battery charging supervision.
- Automatic and manual charging control.
- Temperature-based charging protection.
- Battery State of Charge (SoC) estimation.
- Internal resistance and battery health analysis.
- Regression-based battery health assessment.
- Interactive web dashboard with live data visualization.

## Hardware Components

| Component | Purpose |
|---|---|
| ESP32 | Main controller |
| INA219 (2) | Voltage and current monitoring |
| DS18B20 (2) | Temperature monitoring |
| 4-Channel Relay | Charging path control |
| LM2596 | Adjustable DC voltage regulation |
| Transformer & Bridge Rectifier | AC-to-DC power conversion |
| Filter Capacitor | DC smoothing |
| L7805CV | 5 V voltage regulation |

## Project Resources

- **[ESP32 Firmware](https://github.com/nshakib22ruet/Project-3-Intelligent-Multi-Port-Battery-Management-System/blob/main/Project%20IDE_%20code.ino)**
- **[Web Dashboard](https://github.com/nshakib22ruet/Project-3-Intelligent-Multi-Port-Battery-Management-System/blob/main/Smart_Battery_Dashboard_Improved_Resistance.html)**
- **[Circuit Diagram & Connection Guide](Diagram%20and%20cinnection%20guideline.pdf)**
- **[Project Proposal](Smart_Battery_Charger_Project_Proposal.pdf)**
- **[Project Demonstration Video](https://www.youtube.com/watch?v=hkjahsfdjg)**

## Technologies

`ESP32` `Arduino IDE` `Embedded C/C++` `IoT` `BLE` `INA219` `DS18B20` `HTML` `CSS` `JavaScript` `Regression Analysis`

## Author

**Md. Najmus Shakib**  
Department of Electrical & Electronic Engineering (EEE)  
Rajshahi University of Engineering & Technology (RUET), Bangladesh

GitHub: [@nshakib22ruet](https://github.com/nshakib22ruet)

---

*Developed as an academic hardware and software project focused on intelligent battery monitoring, charging management, and battery health assessment.*
