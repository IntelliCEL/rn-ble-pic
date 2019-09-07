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

import static android.app.Activity.RESULT_OK;
import static android.bluetooth.BluetoothProfile.GATT;

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
	private BLEScanner bleScanner;
    private BLEAdvertiser bleAdvertiser;
	private BondRequest bondRequest;
	private BondRequest removeBondRequest;
    private final Map<String, Peripheral> peripherals = new LinkedHashMap<>();
	private boolean isInitialized;

	public ReactApplicationContext getReactContext() {
		return reactContext;
	}

	public BLEManager(ReactApplicationContext pReactContext) {
		super(pReactContext);
		context = pReactContext;
		reactContext = pReactContext;
		reactContext.addActivityEventListener(this);
		isInitialized = false;
		Log.d(LOG_TAG, "BLEManager created successfully.");
	}

	@Override
	public String getName() {
		return "BLEManager";
	}

	private BluetoothAdapter getBluetoothAdapter() {
		if (bluetoothAdapter == null) {
			BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
			bluetoothAdapter = manager.getAdapter();
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
		getReactApplicationContext().getJSModule(RCTNativeAppEventEmitter.class).emit(eventName, params);
	}

	@ReactMethod
	public void init(Callback callback) {
        if (getBluetoothAdapter() == null || !getBluetoothAdapter().isEnabled()) {
			Log.d(LOG_TAG, "Bluetooth not supported or not enabled.");
			callback.invoke("Bluetooth not supported or not enabled.");
			return;
		}

        bleAdvertiser = new BLEAdvertiser(reactContext, this);
		bleScanner = new BLEScanner(reactContext, this);

		IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		context.registerReceiver(mReceiver, filter);

		isInitialized = true;

		callback.invoke();
		Log.d(LOG_TAG, "BLEManager initialized");
	}

	@ReactMethod
	public void hasInitialized() {
        return isInitialized;
	}

	@ReactMethod
	public void enableBluetooth(Callback callback) {
		if (getBluetoothAdapter() == null) {
			Log.d(LOG_TAG, "Bluetooth not supported.");
			callback.invoke("Bluetooth not supported.");
			return;
		}
		if (!getBluetoothAdapter().isEnabled()) {
			enableBluetoothCallback = callback;
			Intent intentEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			if (getCurrentActivity() == null)
				callback.invoke("Current activity is not available.");
			else
				getCurrentActivity().startActivityForResult(intentEnable, ENABLE_REQUEST);
		} else
			callback.invoke();
	}

    @ReactMethod
	public void checkState() {
		BluetoothAdapter adapter = getBluetoothAdapter();
		String state = "off";
		if (adapter != null) {
			switch (adapter.getState()) {
				case BluetoothAdapter.STATE_ON:
					state = "on";
					break;
				case BluetoothAdapter.STATE_OFF:
					state = "off";
			}
		}

		WritableMap map = Arguments.createMap();
		map.putString("state", state);
		Log.d(LOG_TAG, "state:" + state);
		sendEvent("BLEManagerDidUpdateState", map);
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
						BluetoothAdapter.ERROR);
				String stringState = "";

				switch (state) {
					case BluetoothAdapter.STATE_OFF:
						stringState = "off";
						clearPeripherals();
						break;
					case BluetoothAdapter.STATE_TURNING_OFF:
						stringState = "turning_off";
						disconnectPeripherals();
						break;
					case BluetoothAdapter.STATE_ON:
						stringState = "on";
						break;
					case BluetoothAdapter.STATE_TURNING_ON:
						stringState = "turning_on";
						break;
				}

				WritableMap map = Arguments.createMap();
				map.putString("state", stringState);
				Log.d(LOG_TAG, "state: " + stringState);
				sendEvent("BLEManagerDidUpdateState", map);

			} else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
				final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
				final int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
				BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				String bondStateStr = "UNKNOWN";
				switch (bondState) {
					case BluetoothDevice.BOND_BONDED:
						bondStateStr = "BOND_BONDED";
						break;
					case BluetoothDevice.BOND_BONDING:
						bondStateStr = "BOND_BONDING";
						break;
					case BluetoothDevice.BOND_NONE:
						bondStateStr = "BOND_NONE";
						break;
				}
				Log.d(LOG_TAG, "bond state: " + bondStateStr);

				if (bondRequest != null && bondRequest.uuid.equals(device.getAddress())) {
					if (bondState == BluetoothDevice.BOND_BONDED) {
						bondRequest.callback.invoke();
						bondRequest = null;
					} else if (bondState == BluetoothDevice.BOND_NONE || bondState == BluetoothDevice.ERROR) {
						bondRequest.callback.invoke("Bond request has been denied.");
						bondRequest = null;
					}
				}
				
				if (bondState == BluetoothDevice.BOND_BONDED) {
					Peripheral peripheral = new Peripheral(device, reactContext);
					WritableMap map = peripheral.asWritableMap();
					sendEvent("BLEManagerPeripheralDidBond", map);
				}

				if (removeBondRequest != null && removeBondRequest.uuid.equals(device.getAddress()) && bondState == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED) {
					removeBondRequest.callback.invoke();
					removeBondRequest = null;
				}
			}

		}
	};

    /** Advertiser Methods Start */
    @ReactMethod
    public void setPeripheralName(String name) {
		if(isInitialized) {
        	bleAdvertiser.setPeripheralName(name);
		}
    }

    @ReactMethod
    public void addService(String uuid, Boolean primary, String serviceData) {
        if(isInitialized) {
        	bleAdvertiser.addService(uuid, primary, serviceData);
		}
    }

    @ReactMethod
    public void addCharacteristicToService(String serviceUUID, String characteristicUUID, Integer permissions, Integer properties, String characteristicData) { 
        if(isInitialized) {
        	bleAdvertiser.addCharacteristicToService(serviceUUID, characteristicUUID, permissions, properties, characteristicData);
		}
    } 
	
    @ReactMethod
    public void startAdvertising(final Callback callback) {
        if(isInitialized) {
        	bleAdvertiser.startAdvertising(callback);
		}
    }
	
    @ReactMethod
    public boolean isAdvertising() {
        if(isInitialized) {
        	return bleAdvertiser.isAdvertising();
		}
		return false;
    }
    
    @ReactMethod
    public void stopAdvertising() {
        if(isInitialized) {
        	bleAdvertiser.stopAdvertising();
		}
    }

    /** Advertiser Methods End */
    
    /** Scanner Methods Start */
	@ReactMethod
	public void startScan(ReadableArray serviceUUIDs, final int scanSeconds, ReadableMap options, Callback callback) {

		if (getBluetoothAdapter() == null || !getBluetoothAdapter().isEnabled()) {
			Log.d(LOG_TAG, "Bluetooth not supported or not enabled.");
			callback.invoke("Bluetooth not supported or not enabled.");
			return;
		}

		synchronized (peripherals) {
			for (Iterator<Map.Entry<String, Peripheral>> iterator = peripherals.entrySet().iterator(); iterator.hasNext(); ) {
				Map.Entry<String, Peripheral> entry = iterator.next();
				if (!entry.getValue().isConnected()) {
					iterator.remove();
				}
			}
		}

		if (bleScanner != null) bleScanner.scan(serviceUUIDs, scanSeconds, options, callback);
	}

	@ReactMethod
	public void stopScan(Callback callback) {
		if (getBluetoothAdapter() == null || !getBluetoothAdapter().isEnabled()) {
			Log.d(LOG_TAG, "Bluetooth not supported or not enabled.");
			callback.invoke("Bluetooth not supported or not enabled.");
			return;
		}
		if (bleScanner != null) {
			bleScanner.stopScan(callback);
			WritableMap map = Arguments.createMap();
			sendEvent("BLEManagerStoppedScan", map);
		}
	}

	@ReactMethod
	public void connect(String peripheralUUID, Callback callback) {
		Log.d(LOG_TAG, "Connecting to peripheral: " + peripheralUUID);

		Peripheral peripheral = retrieveOrCreatePeripheral(peripheralUUID);
		if (peripheral != null) {
            peripheral.connect(callback, getCurrentActivity());
		} else {
            callback.invoke("Provided peripheral UUID is invalid or peripheral not found.");
        }
	}

	@ReactMethod
	public void disconnect(String peripheralUUID, boolean force, Callback callback) {
		Log.d(LOG_TAG, "Disconnecting from peripheral: " + peripheralUUID);

		Peripheral peripheral = peripherals.get(peripheralUUID);
		if (peripheral != null) {
			peripheral.disconnect(force);
			callback.invoke();
		} else {
			callback.invoke("Provided peripheral UUID is invalid or peripheral not found.");
        }
	}

    @ReactMethod
	public void retrieveServices(String peripheralUUID, ReadableArray services, Callback callback) {
		Log.d(LOG_TAG, "Retrieving services of peripheral: " + peripheralUUID);
		
        Peripheral peripheral = peripherals.get(peripheralUUID);
		if (peripheral != null) {
			peripheral.retrieveServices(callback);
		} else {
            callback.invoke("Provided peripheral UUID is invalid or peripheral not found.");
        }
	}

	@ReactMethod
	public void startNotification(String peripheralUUID, String serviceUUID, String characteristicUUID, Callback callback) {

		Peripheral peripheral = peripherals.get(peripheralUUID);
		if (peripheral != null) {
			peripheral.registerNotify(Helper.uuidFromString(serviceUUID), Helper.uuidFromString(characteristicUUID), callback);
		} else {
			callback.invoke("Provided peripheral UUID is invalid or peripheral not found.");
        }
	}

	@ReactMethod
	public void stopNotification(String peripheralUUID, String serviceUUID, String characteristicUUID, Callback callback) {

		Peripheral peripheral = peripherals.get(peripheralUUID);
		if (peripheral != null) {
			peripheral.removeNotify(Helper.uuidFromString(serviceUUID), Helper.uuidFromString(characteristicUUID), callback);
		} else {
			callback.invoke("Provided peripheral UUID is invalid or peripheral not found.");
        }
	}

	@ReactMethod
	public void write(String peripheralUUID, String serviceUUID, String characteristicUUID, ReadableArray message, Integer maxByteSize, Callback callback) {
		Log.d(LOG_TAG, "Writing to peripheral: " + peripheralUUID);

		Peripheral peripheral = peripherals.get(peripheralUUID);
		if (peripheral != null) {
			byte[] decoded = new byte[message.size()];
			for (int i = 0; i < message.size(); i++) {
				decoded[i] = new Integer(message.getInt(i)).byteValue();
			}
			Log.d(LOG_TAG, "Message(" + decoded.length + "): " + bytesToHex(decoded));
			peripheral.write(Helper.uuidFromString(serviceUUID), Helper.uuidFromString(characteristicUUID), decoded, maxByteSize, null, callback, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
		} else {
            callback.invoke("Provided peripheral UUID is invalid or peripheral not found.");
        }
	}

	@ReactMethod
	public void writeWithoutResponse(String peripheralUUID, String serviceUUID, String characteristicUUID, ReadableArray message, Integer maxByteSize, Integer queueSleepTime, Callback callback) {
		Log.d(LOG_TAG, "Writing without response to peripheral: " + peripheralUUID);

		Peripheral peripheral = peripherals.get(peripheralUUID);
		if (peripheral != null) {
			byte[] decoded = new byte[message.size()];
			for (int i = 0; i < message.size(); i++) {
				decoded[i] = new Integer(message.getInt(i)).byteValue();
			}
			Log.d(LOG_TAG, "Message(" + decoded.length + "): " + bytesToHex(decoded));
			peripheral.write(Helper.uuidFromString(serviceUUID), Helper.uuidFromString(characteristicUUID), decoded, maxByteSize, queueSleepTime, callback, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		} else {
            callback.invoke("Provided peripheral UUID is invalid or peripheral not found.");
        }
	}

	@ReactMethod
	public void read(String peripheralUUID, String serviceUUID, String characteristicUUID, Callback callback) {
		Log.d(LOG_TAG, "Reading from peripheral: " + peripheralUUID);
		
        Peripheral peripheral = peripherals.get(peripheralUUID);
		if (peripheral != null) {
			peripheral.read(Helper.uuidFromString(serviceUUID), Helper.uuidFromString(characteristicUUID), callback);
		} else {
            callback.invoke("Provided peripheral UUID is invalid or peripheral not found.");
        }
	}

    @ReactMethod
	public void getDiscoveredPeripherals(Callback callback) {
		WritableArray map = Arguments.createArray();
		Map<String, Peripheral> peripheralsCopy = new LinkedHashMap<>(peripherals);
		for (Map.Entry<String, Peripheral> entry : peripheralsCopy.entrySet()) {
			Peripheral peripheral = entry.getValue();
			WritableMap jsonBundle = peripheral.asWritableMap();
			map.pushMap(jsonBundle);
		}
		callback.invoke(null, map);
	}

	@ReactMethod
	public void getConnectedPeripherals(ReadableArray serviceUUIDs, Callback callback) {
		WritableArray map = Arguments.createArray();

        if (getBluetoothAdapter() == null || !getBluetoothAdapter().isEnabled()) {
			Log.d(LOG_TAG, "Bluetooth not supported or not enabled.");
			callback.invoke("Bluetooth not supported or not enabled.");
			return;
		}

		List<BluetoothDevice> periperals = getBluetoothManager().getConnectedDevices(GATT);
		for (BluetoothDevice entry : periperals) {
			Peripheral peripheral = savePeripheral(entry);
			WritableMap jsonBundle = peripheral.asWritableMap();
			map.pushMap(jsonBundle);
		}
		callback.invoke(null, map);
	}

	@ReactMethod
	public void removePeripheral(String peripheralUUID, Callback callback) {
		Log.d(LOG_TAG, "Removing peripheral from list: " + peripheralUUID);

		Peripheral peripheral = peripherals.get(peripheralUUID);
		if (peripheral != null) {
			synchronized (peripherals) {
				if (peripheral.isConnected()) {
					callback.invoke("Peripheral is connected to some client and cannot be removed while connected.");
				} else {
					peripherals.remove(peripheralUUID);
					callback.invoke();
				}
			}
		} else {
            callback.invoke("Provided peripheral UUID is invalid or peripheral not found.");
        }
	}

	private Peripheral savePeripheral(BluetoothDevice device) {
		String address = device.getAddress();
		synchronized (peripherals) {
			if (!peripherals.containsKey(address)) {
				Peripheral peripheral = new Peripheral(device, reactContext);
				peripherals.put(device.getAddress(), peripheral);
			}
		}
		return peripherals.get(address);
	}

	public Peripheral getPeripheral(BluetoothDevice device) {
		String address = device.getAddress();
		return peripherals.get(address);
	}

	public Peripheral savePeripheral(Peripheral peripheral) {
		synchronized (peripherals) {
			peripherals.put(peripheral.getDevice().getAddress(), peripheral);
		}
		return peripheral;
	}

	private void clearPeripherals() {
		if (!peripherals.isEmpty()) {
			synchronized (peripherals) {
				peripherals.clear();
			}
		}
	}

	private void disconnectPeripherals() {
		if (!peripherals.isEmpty()) {
			synchronized (peripherals) {
				for (Peripheral peripheral : peripherals.values()) {
					if (peripheral.isConnected()) {
						peripheral.disconnect(false);
					}
				}
			}
		}
	}

    private Peripheral retrieveOrCreatePeripheral(String peripheralUUID) {
		Peripheral peripheral = peripherals.get(peripheralUUID);
		if (peripheral == null) {
			synchronized (peripherals) {
				if (peripheralUUID != null) {
					peripheralUUID = peripheralUUID.toUpperCase();
				}
				if (BluetoothAdapter.checkBluetoothAddress(peripheralUUID)) {
					BluetoothDevice device = bluetoothAdapter.getRemoteDevice(peripheralUUID);
					peripheral = new Peripheral(device, reactContext);
					peripherals.put(peripheralUUID, peripheral);
				}
			}
		}
		return peripheral;
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
				enableBluetoothCallback.invoke("User refused to enable bluetooth.");
			}
			enableBluetoothCallback = null;
		}
	}

	@Override
	public void onNewIntent(Intent intent) {
	}

    /** Scanner Methods End */
}
