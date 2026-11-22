
package com.nshakib22ruet.batterymanagement;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

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
    name = "BatteryBluetooth",
    permissions = {
        @Permission(
            alias = "bluetooth",
            strings = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            }
        )
    }
)
public class BluetoothPlugin extends Plugin {

    private static final UUID SERVICE_UUID =
        UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");

    private static final UUID TX_UUID =
        UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    private static final UUID RX_UUID =
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");

    private static final UUID CCCD_UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic rxCharacteristic;

    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;

    private PluginCall pendingConnectCall;
    private String connectedDeviceId;

    @PluginMethod
    public void connect(PluginCall call) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            getContext().checkSelfPermission(
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED) {

            pendingConnectCall = call;
            requestPermissionForAlias("bluetooth", call, "bluetoothPermission");
            return;
        }

        startScan(call);
    }

    @PermissionCallback
    private void bluetoothPermission(PluginCall call) {

        if (getPermissionState("bluetooth") !=
            com.getcapacitor.PermissionState.GRANTED) {

            call.reject("Bluetooth permission denied");
            return;
        }

        startScan(call);
    }

    private void startScan(PluginCall call) {

        BluetoothManager manager =
            (BluetoothManager) getContext().getSystemService(
                Context.BLUETOOTH_SERVICE
            );

        BluetoothAdapter adapter = manager.getAdapter();

        if (adapter == null || !adapter.isEnabled()) {
            call.reject("Please turn on Bluetooth");
            return;
        }

        scanner = adapter.getBluetoothLeScanner();

        if (scanner == null) {
            call.reject("Bluetooth scanner unavailable");
            return;
        }

        pendingConnectCall = call;

        scanCallback = new ScanCallback() {

            @Override
            public void onScanResult(int callbackType, ScanResult result) {

                BluetoothDevice device = result.getDevice();

                String deviceName = device.getName();

                if (deviceName != null &&
                    deviceName.startsWith("Smart_Battery")) {

                    scanner.stopScan(this);

                    connectDevice(device, call);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {

                call.reject("Bluetooth scan failed: " + errorCode);
            }
        };

        scanner.startScan(scanCallback);
    }

    private void connectDevice(
        BluetoothDevice device,
        PluginCall call
    ) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            getContext().checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED) {

            call.reject("Bluetooth connect permission missing");
            return;
        }

        bluetoothGatt = device.connectGatt(
            getContext(),
            false,
            gattCallback
        );
    }

    private final BluetoothGattCallback gattCallback =
        new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(
            BluetoothGatt gatt,
            int status,
            int newState
        ) {

            if (status == BluetoothGatt.GATT_SUCCESS &&
                newState == BluetoothProfile.STATE_CONNECTED) {

                connectedDeviceId = gatt.getDevice().getAddress();

                gatt.discoverServices();

            } else if (
                newState == BluetoothProfile.STATE_DISCONNECTED
            ) {

                notifyListeners("disconnected", new JSObject());

                closeGatt();
            }
        }

        @Override
        public void onServicesDiscovered(
            BluetoothGatt gatt,
            int status
        ) {

            if (status != BluetoothGatt.GATT_SUCCESS) {
                rejectPending("Could not discover ESP32 services");
                return;
            }

            android.bluetooth.BluetoothGattService service =
                gatt.getService(SERVICE_UUID);

            if (service == null) {
                rejectPending("ESP32 BLE UART service not found");
                closeGatt();
                return;
            }

            rxCharacteristic = service.getCharacteristic(RX_UUID);

            BluetoothGattCharacteristic txCharacteristic =
                service.getCharacteristic(TX_UUID);

            if (rxCharacteristic == null ||
                txCharacteristic == null) {

                rejectPending("BLE UART characteristics not found");
                closeGatt();
                return;
            }

            boolean notificationEnabled =
                gatt.setCharacteristicNotification(
                    txCharacteristic,
                    true
                );

            BluetoothGattDescriptor descriptor =
                txCharacteristic.getDescriptor(CCCD_UUID);

            if (descriptor == null || !notificationEnabled) {
                rejectPending("Could not enable BLE notifications");
                closeGatt();
                return;
            }

            descriptor.setValue(
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            );

            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                );
            } else {
                gatt.writeDescriptor(descriptor);
            }
        }

        @Override
        public void onDescriptorWrite(
            BluetoothGatt gatt,
            BluetoothGattDescriptor descriptor,
            int status
        ) {

            if (descriptor.getUuid().equals(CCCD_UUID) &&
                status == BluetoothGatt.GATT_SUCCESS) {

                if (pendingConnectCall != null) {

                    JSObject result = new JSObject();
                    result.put("deviceId", connectedDeviceId);

                    pendingConnectCall.resolve(result);
                    pendingConnectCall = null;
                }
            }
        }

        @Override
        public void onCharacteristicChanged(
            BluetoothGatt gatt,
            BluetoothGattCharacteristic characteristic,
            byte[] value
        ) {

            if (characteristic.getUuid().equals(TX_UUID)) {

                String data = new String(
                    value,
                    StandardCharsets.UTF_8
                );

                JSObject event = new JSObject();
                event.put("value", data);

                notifyListeners("dataReceived", event);
            }
        }

        @Override
        public void onCharacteristicChanged(
            BluetoothGatt gatt,
            BluetoothGattCharacteristic characteristic
        ) {

            if (Build.VERSION.SDK_INT < 33) {

                byte[] value = characteristic.getValue();

                if (value != null) {

                    String data = new String(
                        value,
                        StandardCharsets.UTF_8
                    );

                    JSObject event = new JSObject();
                    event.put("value", data);

                    notifyListeners("dataReceived", event);
                }
            }
        }
    };

    @PluginMethod
    public void write(PluginCall call) {

        String value = call.getString("value");

        if (value == null || rxCharacteristic == null ||
            bluetoothGatt == null) {

            call.reject("Bluetooth is not connected");
            return;
        }

        byte[] data = value.getBytes(StandardCharsets.UTF_8);

        if (Build.VERSION.SDK_INT >= 33) {

            int result = bluetoothGatt.writeCharacteristic(
                rxCharacteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            );

            if (result != BluetoothStatusCodes.SUCCESS) {
                call.reject("Bluetooth write failed: " + result);
                return;
            }

        } else {

            rxCharacteristic.setWriteType(
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            );

            rxCharacteristic.setValue(data);

            if (!bluetoothGatt.writeCharacteristic(rxCharacteristic)) {
                call.reject("Bluetooth write failed");
                return;
            }
        }

        call.resolve();
    }

    @PluginMethod
    public void disconnect(PluginCall call) {

        closeGatt();
        call.resolve();
    }

    private void rejectPending(String message) {

        if (pendingConnectCall != null) {
            pendingConnectCall.reject(message);
            pendingConnectCall = null;
        }
    }

    private void closeGatt() {

        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        rxCharacteristic = null;
        connectedDeviceId = null;
    }

    @Override
    protected void handleOnDestroy() {
        closeGatt();
        super.handleOnDestroy();
    }
}
