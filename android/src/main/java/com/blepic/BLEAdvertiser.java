package com.blepic;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import static android.app.Activity.RESULT_OK;
import static android.bluetooth.BluetoothProfile.GATT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;

class BLEAdvertiser {

	public static final String LOG_TAG = "BLE_PIC";

	private Context context;
	private ReactApplicationContext reactContext;
    protected BLEManager bleManager;

    private BluetoothAdapter bluetoothAdapter;
	private BluetoothManager bluetoothManager;

	private String peripheralName;
	private HashMap<String, BluetoothGattService> servicesMap;
    private HashMap<String, String> servicesDataMap;
    private HashSet<BluetoothDevice> bluetoothDevices;
	private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertisingCallback;
	private boolean advertising;

	private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    bluetoothDevices.add(device);
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    bluetoothDevices.remove(device);
                }
            } else {
                bluetoothDevices.remove(device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (offset != 0) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null);
                return;
            }
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            characteristic.setValue(value);
            WritableMap map = Arguments.createMap();
            WritableArray data = Arguments.createArray();
            for (byte b : value) {
                data.pushInt((int) b);
            }
            map.putArray("data", data);
            map.putString("device", device.toString());
            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }
    };

	public BLEAdvertiser(ReactApplicationContext pReactContext, BLEManager pBleManager) {
		context = pReactContext;
		reactContext = pReactContext;
		bleManager = pBleManager;
		servicesMap = new HashMap<String, BluetoothGattService>();
        servicesDataMap = new HashMap<String, String>();
		advertising = false;
    	peripheralName = null;
	}

    public void setPeripheralName(String name) {
        peripheralName = name;
    }

    public void addService(String uuid, Boolean primary, String serviceData) {
        UUID SERVICE_UUID = UUID.fromString(uuid);
        int type = primary ? BluetoothGattService.SERVICE_TYPE_PRIMARY : BluetoothGattService.SERVICE_TYPE_SECONDARY;
        BluetoothGattService tempService = new BluetoothGattService(SERVICE_UUID, type);
        if(!this.servicesMap.containsKey(uuid))
            this.servicesMap.put(uuid, tempService);

        if(!this.servicesDataMap.containsKey(uuid) && serviceData != null)
            this.servicesDataMap.put(uuid, serviceData);
    }

    public void addCharacteristicToService(String serviceUUID, String characteristicUUID, Integer permissions, Integer properties, String characteristicData) {  
		UUID CHAR_UUID = UUID.fromString(characteristicUUID);

        if(this.servicesMap.get(serviceUUID).getCharacteristic(CHAR_UUID) != null) {
            if(characteristicData != null && characteristicData != "") {
                this.servicesMap.get(serviceUUID).getCharacteristic(CHAR_UUID).setValue(characteristicData);
            }
        } else {
            BluetoothGattCharacteristic tempChar = new BluetoothGattCharacteristic(CHAR_UUID, properties, permissions);
		
            if(characteristicData != null && characteristicData != "") {
                tempChar.setValue(characteristicData);
            }

            this.servicesMap.get(serviceUUID).addCharacteristic(tempChar);
        }
    }

    public void startAdvertising(final Callback callback) {
		bluetoothManager = getBluetoothManager();
		bluetoothAdapter = getBluetoothAdapter();

        // Ensures if Bluetooth is available on the device and also it is enabled.
		if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
			Log.d(LOG_TAG, "Bluetooth not supported or not enabled.");
			callback.invoke("Bluetooth not supported or not enabled.");
			return;
		}

		if(peripheralName != null) {
        	bluetoothAdapter.setName(peripheralName);
		}

        bluetoothDevices = new HashSet<>();
        // gattServer = getBluetoothGattServer();
        gattServer = bluetoothManager.openGattServer(reactContext, gattServerCallback);
        for (BluetoothGattService service : this.servicesMap.values()) {
            gattServer.addService(service);
        }
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();

        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder()
                .setIncludeDeviceName(true);
        for (Map.Entry<String,BluetoothGattService> entry : this.servicesMap.entrySet()) {
            BluetoothGattService service = entry.getValue();
            String uuid = entry.getKey();
            dataBuilder.addServiceUuid(new ParcelUuid(service.getUuid()));

            String serviceData = this.servicesDataMap.get(uuid);
            if(serviceData != null) {
                byte[] b = serviceData.getBytes();
                dataBuilder.addServiceData(new ParcelUuid(service.getUuid()), b);
            }
        }
        AdvertiseData data = dataBuilder.build();
        Log.i("RNBLEModule", data.toString());

        advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                advertising = true;
                callback.invoke();
            }

            @Override
            public void onStartFailure(int errorCode) {
                advertising = false;
                Log.e(bleManager.LOG_TAG, "Advertising onStartFailure: " + errorCode);
				callback.invoke("Advertising onStartFailure: " + errorCode);
                super.onStartFailure(errorCode);
            }
        };

        advertiser.startAdvertising(settings, data, advertisingCallback);
    }

    public boolean isAdvertising() {
        return advertising;
    }

    public void stopAdvertising() {
        if (gattServer != null) {
            gattServer.close();
        }
		bluetoothAdapter = getBluetoothAdapter();
        if (bluetoothAdapter !=null && bluetoothAdapter.isEnabled() && advertiser != null) {
            // GATT server must be closed befor stoping advertising
            advertiser.stopAdvertising(advertisingCallback);
            advertising = false;
        }
    }

	private BluetoothGattServer getBluetoothGattServer() {
		if (gattServer == null) {
			gattServer = getBluetoothManager().openGattServer(reactContext, gattServerCallback);
		}
		return gattServer;
	}

	private BluetoothAdapter getBluetoothAdapter() {
		if (bluetoothAdapter == null) {
			bluetoothAdapter = getBluetoothManager().getAdapter();
		}
		return bluetoothAdapter;
	}

	private BluetoothManager getBluetoothManager() {
		if (bluetoothManager == null) {
			bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		}
		return bluetoothManager;
	}
}
