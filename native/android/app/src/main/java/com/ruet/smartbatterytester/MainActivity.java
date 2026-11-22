
package com.ruet.smartbatterytester;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.nshakib22ruet.batterymanagement.BluetoothPlugin;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(BluetoothPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
