package com.blepic;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.annotation.Nullable;
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

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;


class BLEManager extends ReactContextBaseJavaModule implements ActivityEventListener {

	public static final String LOG_TAG = "BLE_PIC";
	private static final int ENABLE_REQUEST = 539;

	private class BondRequest {
		private String uuid;
		private Callback callback;

		BondRequest(String _uuid, Callback _callback) {
			uuid = _uuid;
			callback = _callback;
		}
	}

	private BluetoothAdapter bluetoothAdapter;
	private BluetoothManager bluetoothManager;
	private Context context;
	private ReactApplicationContext reactContext;
	private Callback enableBluetoothCallback;
	private BondRequest bondRequest;
	private BondRequest removeBondRequest;
	private boolean forceLegacy;

	private String name;
	HashMap<String, BluetoothGattService> servicesMap;
    HashMap<String, String> servicesDataMap;
    HashSet<BluetoothDevice> bluetoothDevices;
	BluetoothGattServer gattServer;
    BluetoothLeAdvertiser advertiser;
    AdvertiseCallback advertisingCallback;
	boolean advertising;

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

	public ReactApplicationContext getReactContext() {
		return reactContext;
	}

	public BLEManager(ReactApplicationContext reactContext) {
		super(reactContext);
		this.context = reactContext;
		this.reactContext = reactContext;
		reactContext.addActivityEventListener(this);
		this.servicesMap = new HashMap<String, BluetoothGattService>();
        this.servicesDataMap = new HashMap<String, String>();
		this.advertising = false;
    	this.name = null;
		Log.d(LOG_TAG, "BLEManager created");
	}

	@Override
	public String getName() {
		return "BLEManager";
	}

	@ReactMethod
    public void setPeripheralName(String name) {
        this.name = name;
    }

	@ReactMethod
    public void addService(String uuid, Boolean primary, String serviceData) {
        UUID SERVICE_UUID = UUID.fromString(uuid);
        int type = primary ? BluetoothGattService.SERVICE_TYPE_PRIMARY : BluetoothGattService.SERVICE_TYPE_SECONDARY;
        BluetoothGattService tempService = new BluetoothGattService(SERVICE_UUID, type);
        if(!this.servicesMap.containsKey(uuid))
            this.servicesMap.put(uuid, tempService);

        if(!this.servicesDataMap.containsKey(uuid) && serviceData != null)
            this.servicesDataMap.put(uuid, serviceData);
    }

    @ReactMethod
    public void addCharacteristicToService(String serviceUUID, String uuid, Integer permissions, Integer properties, String data) {  
		UUID CHAR_UUID = UUID.fromString(uuid);
        BluetoothGattCharacteristic tempChar = new BluetoothGattCharacteristic(CHAR_UUID, properties, permissions);
		
        if(data != null && data != "") {
          tempChar.setValue(data);
        }

        this.servicesMap.get(serviceUUID).addCharacteristic(tempChar);
    }

	@ReactMethod
    public void startAdvertising(final Callback callback) {
		bluetoothManager = getBluetoothManager();
		bluetoothAdapter = getBluetoothAdapter();

        // Ensures if Bluetooth is available on the device and also it is enabled.
		if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
			Log.d(LOG_TAG, "Bluetooth not supported or not enabled.");
			callback.invoke("Bluetooth not supported or not enabled.");
			return;
		}

		if(this.name != null) {
        	bluetoothAdapter.setName(this.name);
		}

        bluetoothDevices = new HashSet<>();
        gattServer = getBluetoothGattServer();
        //bluetoothManager.openGattServer(reactContext, gattServerCallback);
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
                Log.e("RNBLEModule", "Advertising onStartFailure: " + errorCode);
				callback.invoke("Advertising onStartFailure: " + errorCode);
                super.onStartFailure(errorCode);
            }
        };

        advertiser.startAdvertising(settings, data, advertisingCallback);
    }

	@ReactMethod
    public boolean isAdvertising() {
        return this.advertising;
    }

	@ReactMethod
    public void stopAdvertising() {
        if (gattServer != null) {
            gattServer.clearServices();
            gattServer.close();
            gattServer = null;
        }
		bluetoothAdapter = getBluetoothAdapter();
        if (bluetoothAdapter !=null && bluetoothAdapter.isEnabled() && advertiser != null) {
            // Calling stopAdvertising() before calling GATT server's close() method will throw a null pointer exception
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

	public void sendEvent(String eventName, @Nullable WritableMap params) {
		getReactApplicationContext()
				.getJSModule(RCTNativeAppEventEmitter.class)
				.emit(eventName, params);
	}

	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static WritableArray bytesToWritableArray(byte[] bytes) {
		WritableArray value = Arguments.createArray();
		for (int i = 0; i < bytes.length; i++)
			value.pushInt((bytes[i] & 0xFF));
		return value;
	}

	@Override
	public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
		Log.d(LOG_TAG, "onActivityResult");
		if (requestCode == ENABLE_REQUEST && enableBluetoothCallback != null) {
			if (resultCode == RESULT_OK) {
				enableBluetoothCallback.invoke();
			} else {
				enableBluetoothCallback.invoke("User refused to enable");
			}
			enableBluetoothCallback = null;
		}
	}

	@Override
	public void onNewIntent(Intent intent) {

	}
}
