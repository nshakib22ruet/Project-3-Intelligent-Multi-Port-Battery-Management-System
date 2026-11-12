#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <OneWire.h>
#include <DallasTemperature.h>

// ================= BLE UUIDs =================
#define SERVICE_UUID "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define CHARACTERISTIC_UUID_RX "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
#define CHARACTERISTIC_UUID_TX "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

BLECharacteristic *pTxCharacteristic;
bool deviceConnected = false;

// ================= I2C BUSES (INA219) =================
TwoWire I2C_1 = TwoWire(0);
TwoWire I2C_2 = TwoWire(1);

#define INA1_SDA 32
#define INA1_SCL 33

#define INA2_SDA 19
#define INA2_SCL 18

uint8_t addr1 = 0x40;
uint8_t addr2 = 0x41;

// INA219 Registers
#define INA219_REG_CONFIG           0x00
#define INA219_REG_SHUNTVOLTAGE     0x01
#define INA219_REG_BUSVOLTAGE       0x02
#define INA219_REG_POWER            0x03
#define INA219_REG_CURRENT          0x04
#define INA219_REG_CALIBRATION      0x05

// ================= RELAY PINS =================
#define RELAY1 12
#define RELAY2 14
#define RELAY3 27
#define RELAY4 26

// ================= ONEWIRE / TEMPERATURE SENSORS =================
OneWire oneWire1(4);
OneWire oneWire2(15);

DallasTemperature tempSensor1(&oneWire1);
DallasTemperature tempSensor2(&oneWire2);

// ================= BLE CALLBACKS =================
class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) { deviceConnected = true; Serial.println("BLE Connected"); }
  void onDisconnect(BLEServer* pServer) { deviceConnected = false; Serial.println("BLE Disconnected"); pServer->getAdvertising()->start(); }
};

class MyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    String rxValue = pCharacteristic->getValue();
    if (rxValue.length() > 0) {
      rxValue.trim();
      if (rxValue == "RELAY1:ON") { digitalWrite(RELAY1, LOW); Serial.println("Relay 1 ON"); }
      else if (rxValue == "RELAY1:OFF") { digitalWrite(RELAY1, HIGH); Serial.println("Relay 1 OFF"); }
      else if (rxValue == "RELAY2:ON") { digitalWrite(RELAY2, LOW); Serial.println("Relay 2 ON"); }
      else if (rxValue == "RELAY2:OFF") { digitalWrite(RELAY2, HIGH); Serial.println("Relay 2 OFF"); }
      else if (rxValue == "RELAY3:ON") { digitalWrite(RELAY3, LOW); Serial.println("Relay 3 ON"); }
      else if (rxValue == "RELAY3:OFF") { digitalWrite(RELAY3, HIGH); Serial.println("Relay 3 OFF"); }
      else if (rxValue == "RELAY4:ON") { digitalWrite(RELAY4, LOW); Serial.println("Relay 4 ON"); }
      else if (rxValue == "RELAY4:OFF") { digitalWrite(RELAY4, HIGH); Serial.println("Relay 4 OFF"); }
    }
  }
};

// Write to INA219 Register
void writeRegister(TwoWire &wirePort, uint8_t addr, uint8_t reg, uint16_t value) {
  wirePort.beginTransmission(addr);
  wirePort.write(reg);
  wirePort.write((value >> 8) & 0xFF);
  wirePort.write(value & 0xFF);
  wirePort.endTransmission();
}

// Read from INA219 Register
uint16_t readRegister(TwoWire &wirePort, uint8_t addr, uint8_t reg) {
  wirePort.beginTransmission(addr);
  wirePort.write(reg);
  wirePort.endTransmission(false);
  wirePort.requestFrom(addr, (uint8_t)2);
  if (wirePort.available() >= 2) {
    uint16_t value = wirePort.read() << 8;
    value |= wirePort.read();
    return value;
  }
  return 0;
}

void setupSensor(TwoWire &wirePort, uint8_t addr) {
  writeRegister(wirePort, addr, INA219_REG_CALIBRATION, 4096);
  writeRegister(wirePort, addr, INA219_REG_CONFIG, 0x399F); // 32V, 32A, 12-bit
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  // Initialize Relays
  pinMode(RELAY1, OUTPUT); pinMode(RELAY2, OUTPUT);
  pinMode(RELAY3, OUTPUT); pinMode(RELAY4, OUTPUT);
  digitalWrite(RELAY1, HIGH); digitalWrite(RELAY2, HIGH);
  digitalWrite(RELAY3, HIGH); digitalWrite(RELAY4, HIGH);

  // Initialize I2C Buses & INA219 Sensors
  I2C_1.begin(INA1_SDA, INA1_SCL, 100000);
  I2C_2.begin(INA2_SDA, INA2_SCL, 100000);
  setupSensor(I2C_1, addr1);
  setupSensor(I2C_2, addr2);

  // Initialize DS18B20 Temperature Sensors
  tempSensor1.begin();
  tempSensor2.begin();
  tempSensor1.setWaitForConversion(false);
  tempSensor2.setWaitForConversion(false);

  // Initialize BLE
  BLEDevice::init("Smart_Battery_System");
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  BLEService *pService = pServer->createService(SERVICE_UUID);

  pTxCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_TX, BLECharacteristic::PROPERTY_NOTIFY);
  pTxCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_RX, BLECharacteristic::PROPERTY_WRITE);
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  pService->start();
  pServer->getAdvertising()->start();
  Serial.println("System Initialized & Ready!");
}

void readINA219Data(TwoWire &wirePort, uint8_t addr, int sensorNum, String &outBuffer) {
  uint16_t busVoltRaw = readRegister(wirePort, addr, INA219_REG_BUSVOLTAGE);
  float busVoltage = (busVoltRaw >> 3) * 0.004; // 4mV per bit

  int16_t shuntVoltRaw = (int16_t)readRegister(wirePort, addr, INA219_REG_SHUNTVOLTAGE);
  float shuntVoltage_mV = shuntVoltRaw * 0.01; // 0.01mV per bit (10 µV per bit)
  float loadVoltage = busVoltage + (shuntVoltage_mV / 1000.0);
  float current_mA = shuntVoltage_mV / 0.1; // 0.1 ohm shunt resistor
  float power_mW = busVoltage * current_mA;

  outBuffer += "INA Sensor " + String(sensorNum) + ":\n";
  outBuffer += "Bus Voltage: " + String(busVoltage, 3) + " V\n";
  outBuffer += "Load Voltage: " + String(loadVoltage, 3) + " V\n";
  outBuffer += "Current: " + String(current_mA, 2) + " mA\n";
  outBuffer += "Power: " + String(power_mW, 2) + " mW\n";
}

void loop() {
  Serial.println("========== System Measurements ==========");

  // 1. Trigger temperature conversions asynchronously
  tempSensor1.requestTemperatures();
  tempSensor2.requestTemperatures();

  // 2. Read INA219 current/voltage sensors
  String out = "";
  readINA219Data(I2C_1, addr1, 1, out);
  readINA219Data(I2C_2, addr2, 2, out);

  // 3. Wait for temperature conversion to complete (non-blocking delay split)
  delay(800);

  // 4. Read temperature values
  float temp1 = tempSensor1.getTempCByIndex(0);
  float temp2 = tempSensor2.getTempCByIndex(0);

  out += "Temp 1 (GPIO 4): ";
  if (temp1 == DEVICE_DISCONNECTED_C) {
    out += "Not detected!\n";
  } else {
    out += String(temp1, 2) + " C\n";
  }

  out += "Temp 2 (GPIO 15): ";
  if (temp2 == DEVICE_DISCONNECTED_C) {
    out += "Not detected!\n";
  } else {
    out += String(temp2, 2) + " C\n";
  }

  // Print to Serial Monitor
  Serial.println(out);
  Serial.println("------------------------------------------");

  // Send aggregated data via BLE Notification if connected
  if (deviceConnected) {
    pTxCharacteristic->setValue((uint8_t*)out.c_str(), out.length());
    pTxCharacteristic->notify();
  }

  delay(1200); // Remaining delay interval to complete ~2 second cadence
}