package com.example.dario_dell.wristwatch;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;

public class ConnManager {


    public static Intent checkIfEnabled() {
        if (!getBluteoothAdapter().isEnabled()) {
            return new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        }
        return null;

    }

    private static BluetoothAdapter getBluteoothAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }
}
