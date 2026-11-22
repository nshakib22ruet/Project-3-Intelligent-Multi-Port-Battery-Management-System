
package com.nshakib.batterymanagement;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@CapacitorPlugin(
    name = "BluetoothPlugin",
    permissions = {
        @Permission(
            alias = "bluetoothScan",
            strings = { Manifest.permission.BLUETOOTH_SCAN }
        ),
        @Permission(
            alias = "bluetoothConnect",
            strings = { Manifest.permission.BLUETOOTH_CONNECT }
        ),
        @Permission(
            alias = "location",
            strings = { Manifest.permission.ACCESS_FINE_LOCATION }
        )
    }
)
public class BluetoothPlugin extends Plugin {

    private static final String DEVICE_PREFIX = "Smart_Battery";

    private static final UUID SERVICE_UUID =
        UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");

    private static final UUID TX_UUID =
        UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    private static final UUID RX_UUID =
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");

    private static final UUID CCCD_UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic txCharacteristic;
    private BluetoothGattCharacteristic rxCharacteristic;

    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private PluginCall pendingScanCall;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @PluginMethod
    public void scan(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionForAliases(
                new String[]{"bluetoothScan", "bluetoothConnect"},
                call,
                "scanPermissionCallback"
            );
        } else {
            requestPermissionForAlias("location", call, "scanPermissionCallback");
        }
    }

    @PermissionCallback
    private void scanPermissionCallback(PluginCall call) {
        startScan(call);
    }

    private void startScan(PluginCall call) {
        BluetoothManager manager =
            (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);

        BluetoothAdapter adapter = manager.getAdapter();

        if (adapter == null || !adapter.isEnabled()) {
            call.reject("Please turn on Bluetooth.");
            return;
        }

        scanner = adapter.getBluetoothLeScanner();

        if (scanner == null) {
            call.reject("Bluetooth scanner is unavailable.");
            return;
        }

        if (pendingScanCall != null) {
            stopCurrentScan();
        }

        pendingScanCall = call;

        ScanSettings settings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                String name = device.getName();

                if (name == null || !name.startsWith(DEVICE_PREFIX)) {
                    return;
                }

                stopCurrentScan();

                JSObject response = new JSObject();
                response.put("deviceId", device.getAddress());
                response.put("name", name);

                if (pendingScanCall != null) {
                    pendingScanCall.resolve(response);
                    pendingScanCall = null;
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                stopCurrentScan();

                if (pendingScanCall != null) {
                    pendingScanCall.reject("Bluetooth scan failed: " + errorCode);
                    pendingScanCall = null;
                }
            }
        };

        try {
            scanner.startScan(null, settings, scanCallback);

            handler.postDelayed(() -> {
                if (pendingScanCall == call) {
                    stopCurrentScan();
                    pendingScanCall = null;
                    call.reject("No Smart_Battery device found. Check ESP32.");
                }
            }, 15000);

        } catch (SecurityException e) {
            stopCurrentScan();
            pendingScanCall = null;
            call.reject("Bluetooth permission denied.");
        }
    }

    private void stopCurrentScan() {
        if (scanner != null && scanCallback != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception ignored) {
            }
        }
        scanCallback = null;
    }

    @PluginMethod
    public void connect(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionForAlias(
                "bluetoothConnect",
                call,
                "connectPermissionCallback"
            );
        } else {
            connectNow(call);
        }
    }

    @PermissionCallback
    private void connectPermissionCallback(PluginCall call) {
        connectNow(call);
    }

    private void connectNow(PluginCall call) {
        String address = call.getString("deviceId");

        if (address == null || address.isEmpty()) {
            call.reject("Device ID is missing.");
            return;
        }

        try {
            BluetoothManager manager =
                (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);

            BluetoothAdapter adapter = manager.getAdapter();
            BluetoothDevice device = adapter.getRemoteDevice(address);

            bluetoothGatt = device.connectGatt(
                getContext(),
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            );

            if (bluetoothGatt == null) {
                call.reject("Could not start BLE connection.");
                return;
            }

            pendingConnectCall = call;

        } catch (Exception e) {
            call.reject("Connection failed: " + e.getMessage());
        }
    }

    private PluginCall pendingConnectCall;

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(
            BluetoothGatt gatt,
            int status,
            int newState
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                txCharacteristic = null;
                rxCharacteristic = null;

                JSObject data = new JSObject();
                data.put("connected", false);
                notifyListeners("bleDisconnected", data);

                if (pendingConnectCall != null) {
                    pendingConnectCall.reject("BLE device disconnected.");
                    pendingConnectCall = null;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (pendingConnectCall != null) {
                    pendingConnectCall.reject("Could not discover BLE services.");
                    pendingConnectCall = null;
                }
                return;
            }

            BluetoothGattService service = gatt.getService(SERVICE_UUID);

            if (service == null) {
                if (pendingConnectCall != null) {
                    pendingConnectCall.reject("Required BLE service not found.");
                    pendingConnectCall = null;
                }
                return;
            }

            txCharacteristic = service.getCharacteristic(TX_UUID);
            rxCharacteristic = service.getCharacteristic(RX_UUID);

            if (txCharacteristic == null || rxCharacteristic == null) {
                if (pendingConnectCall != null) {
                    pendingConnectCall.reject("BLE characteristics not found.");
                    pendingConnectCall = null;
                }
                return;
            }

            if (pendingConnectCall != null) {
                pendingConnectCall.resolve();
                pendingConnectCall = null;
            }
        }

        @Override
        public void onCharacteristicChanged(
            BluetoothGatt gatt,
            BluetoothGattCharacteristic characteristic
        ) {
            byte[] value = characteristic.getValue();
            sendNotification(value);
        }

        @Override
        public void onCharacteristicChanged(
            BluetoothGatt gatt,
            BluetoothGattCharacteristic characteristic,
            byte[] value
        ) {
            sendNotification(value);
        }

        @Override
        public void onDescriptorWrite(
            BluetoothGatt gatt,
            BluetoothGattDescriptor descriptor,
            int status
        ) {
            if (pendingNotifyCall != null) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingNotifyCall.resolve();
                } else {
                    pendingNotifyCall.reject("Could not enable BLE notifications.");
                }
                pendingNotifyCall = null;
            }
        }
    };

    private void sendNotification(byte[] value) {
        if (value == null) return;

        JSObject data = new JSObject();
        data.put("value", new String(value, StandardCharsets.UTF_8));
        notifyListeners("bleData", data);
    }

    private PluginCall pendingNotifyCall;

    @PluginMethod
    public void startNotifications(PluginCall call) {
        if (bluetoothGatt == null || txCharacteristic == null) {
            call.reject("BLE device is not connected.");
            return;
        }

        try {
            bluetoothGatt.setCharacteristicNotification(txCharacteristic, true);

            BluetoothGattDescriptor descriptor =
                txCharacteristic.getDescriptor(CCCD_UUID);

            if (descriptor == null) {
                call.reject("Notification descriptor not found.");
                return;
            }

            pendingNotifyCall = call;

            if (Build.VERSION.SDK_INT >= 33) {
                int result = bluetoothGatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                );

                if (result != BluetoothStatusCodes.SUCCESS) {
                    pendingNotifyCall = null;
                    call.reject("Could not enable notifications.");
                }
            } else {
                descriptor.setValue(
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                );

                if (!bluetoothGatt.writeDescriptor(descriptor)) {
                    pendingNotifyCall = null;
                    call.reject("Could not enable notifications.");
                }
            }

        } catch (Exception e) {
            pendingNotifyCall = null;
            call.reject("Notification error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        if (bluetoothGatt == null || rxCharacteristic == null) {
            call.reject("BLE device is not connected.");
            return;
        }

        String value = call.getString("value", "");

        try {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

            int properties = rxCharacteristic.getProperties();

            if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0
                && (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                call.reject("BLE characteristic does not support writing.");
                return;
            }

            if (Build.VERSION.SDK_INT >= 33) {
                int writeType =
                    (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                    ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;

                int result = bluetoothGatt.writeCharacteristic(
                    rxCharacteristic,
                    bytes,
                    writeType
                );

                if (result == BluetoothStatusCodes.SUCCESS) {
                    call.resolve();
                } else {
                    call.reject("BLE write failed: " + result);
                }

            } else {
                rxCharacteristic.setWriteType(
                    (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                    ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                );

                rxCharacteristic.setValue(bytes);

                if (bluetoothGatt.writeCharacteristic(rxCharacteristic)) {
                    call.resolve();
                } else {
                    call.reject("BLE write could not be started.");
                }
            }

        } catch (Exception e) {
            call.reject("Write error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        try {
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }

            txCharacteristic = null;
            rxCharacteristic = null;

            call.resolve();

        } catch (Exception e) {
            call.reject("Disconnect error: " + e.getMessage());
        }
    }
}
