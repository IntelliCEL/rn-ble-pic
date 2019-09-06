package com.blepic;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanRecord;
import android.content.Context;
import android.os.ParcelUuid;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

import org.json.JSONException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.facebook.react.common.ReactConstants.TAG;

public class Peripheral extends BluetoothGattCallback {

	private static final String CHARACTERISTIC_NOTIFICATION_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

	private final BluetoothDevice device;

	protected byte[] advertisingDataBytes = new byte[0];
	protected int advertisingRSSI;
	private boolean connected = false;

	private ReactContext reactContext;
	private BluetoothGatt gatt;
	private ScanRecord advertisingData;

	private Callback connectCallback;
	private Callback retrieveServicesCallback;
	private Callback readCallback;
	private Callback readRSSICallback;
	private Callback writeCallback;
	private Callback registerNotifyCallback;
	private Callback requestMTUCallback;

	private List<byte[]> writeQueue = new ArrayList<>();

	public Peripheral(BluetoothDevice device, int advertisingRSSI, ScanRecord scanRecord, ReactContext reactContext) {
		this.device = device;
		this.reactContext = reactContext;
		this.advertisingRSSI = advertisingRSSI;
		this.advertisingData = scanRecord;
		this.advertisingDataBytes = scanRecord.getBytes();
	}

	public Peripheral(BluetoothDevice device, ReactContext reactContext) {
		this.device = device;
		this.reactContext = reactContext;
	}

	private void sendEvent(String eventName, @Nullable WritableMap params) {
		reactContext.getJSModule(RCTNativeAppEventEmitter.class).emit(eventName, params);
	}

	private void sendConnectionEvent(BluetoothDevice device, String eventName, int status) {
		WritableMap map = Arguments.createMap();
		map.putString("peripheral", device.getAddress());
		if (status != -1) {
			map.putInt("status", status);
		}
		sendEvent(eventName, map);
		Log.d(BLEManager.LOG_TAG, "Peripheral event (" + eventName + "):" + device.getAddress());
	}

	public void connect(Callback callback, Activity activity) {
		if (!connected) {
			BluetoothDevice device = getDevice();
			this.connectCallback = callback;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				Log.d(BLEManager.LOG_TAG, " Is Or Greater than M $mBluetoothDevice");
				gatt = device.connectGatt(activity, false, this, BluetoothDevice.TRANSPORT_LE);
			} else {
				Log.d(BLEManager.LOG_TAG, " Less than M");
				try {
					Log.d(BLEManager.LOG_TAG, " Trying TRANPORT LE with reflection");
					Method m = device.getClass().getDeclaredMethod("connectGatt", Context.class, Boolean.class, BluetoothGattCallback.class, Integer.class);
					m.setAccessible(true);
					Integer transport = device.getClass().getDeclaredField("TRANSPORT_LE").getInt(null);
					gatt = (BluetoothGatt) m.invoke(device, activity, false, this, transport);
				} catch (Exception e) {
					e.printStackTrace();
					Log.d(TAG, " Catch to call normal connection");
					gatt = device.connectGatt(activity, false,
							this);
				}
			}
		} else {
			if (gatt != null) {
				callback.invoke();
			} else {
				callback.invoke("BluetoothGatt is null");
			}
		}
	}

	public void disconnect(boolean force) {
		connectCallback = null;
		connected = false;
		if (gatt != null) {
			try {
				gatt.disconnect();
				if (force) {
					gatt.close();
					gatt = null;
					sendConnectionEvent(device, "BLEManagerDisconnectPeripheral", BluetoothGatt.GATT_SUCCESS);
				}
				Log.d(BLEManager.LOG_TAG, "Disconnect");
			} catch (Exception e) {
				sendConnectionEvent(device, "BLEManagerDisconnectPeripheral", BluetoothGatt.GATT_FAILURE);
				Log.d(BLEManager.LOG_TAG, "Error on disconnect", e);
			}
		} else
			Log.d(BLEManager.LOG_TAG, "GATT is null");
	}

	public WritableMap asWritableMap() {
		WritableMap map = Arguments.createMap();
		WritableMap advertising = Arguments.createMap();

		try {
			map.putString("name", device.getName());
			map.putString("id", device.getAddress()); // mac address of the peripheral
			map.putInt("rssi", advertisingRSSI);

			advertising.putMap("manufacturerData", byteArrayToWritableMap(advertisingDataBytes));
			advertising.putBoolean("isConnectable", true);

			if (advertisingData != null) {

				advertising.putInt("txPowerLevel", advertisingData.getTxPowerLevel());

				String deviceName = advertisingData.getDeviceName();
				if (deviceName != null) {
					advertising.putString("localName", deviceName.replace("\0", ""));
				}

				WritableArray serviceUuids = Arguments.createArray();
				if (advertisingData.getServiceUuids() != null && advertisingData.getServiceUuids().size() != 0) {
					for (ParcelUuid uuid : advertisingData.getServiceUuids()) {
						serviceUuids.pushString(Helper.uuidToString(uuid.getUuid()));
					}
				}
				advertising.putArray("serviceUUIDs", serviceUuids);

				WritableMap serviceData = Arguments.createMap();
				if (advertisingData.getServiceData() != null) {
					for (Map.Entry<ParcelUuid, byte[]> entry : advertisingData.getServiceData().entrySet()) {
						if (entry.getValue() != null) {
							serviceData.putMap(Helper.uuidToString((entry.getKey()).getUuid()), byteArrayToWritableMap(entry.getValue()));
						}
					}
				}

				advertising.putMap("serviceData", serviceData);
			}

			map.putMap("advertising", advertising);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return map;
	}

	public WritableMap asWritableMap(BluetoothGatt gatt) {

		WritableMap map = asWritableMap();
		WritableArray servicesArray = Arguments.createArray();
		WritableArray characteristicsArray = Arguments.createArray();

		if (connected && gatt != null) {
			for (Iterator<BluetoothGattService> it = gatt.getServices().iterator(); it.hasNext(); ) {
				BluetoothGattService service = it.next();
				WritableMap serviceMap = Arguments.createMap();
				serviceMap.putString("uuid", Helper.uuidToString(service.getUuid()));

				for (Iterator<BluetoothGattCharacteristic> itCharacteristic = service.getCharacteristics().iterator(); itCharacteristic.hasNext(); ) {
					BluetoothGattCharacteristic characteristic = itCharacteristic.next();
					WritableMap characteristicsMap = Arguments.createMap();

					characteristicsMap.putString("service", Helper.uuidToString(service.getUuid()));
					characteristicsMap.putString("characteristic", Helper.uuidToString(characteristic.getUuid()));
					characteristicsMap.putMap("properties", Helper.decodeProperties(characteristic));

					if (characteristic.getPermissions() > 0) {
						characteristicsMap.putMap("permissions", Helper.decodePermissions(characteristic));
					}

					WritableArray descriptorsArray = Arguments.createArray();

					for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
						WritableMap descriptorMap = Arguments.createMap();
						descriptorMap.putString("uuid", Helper.uuidToString(descriptor.getUuid()));
						if (descriptor.getValue() != null) {
							descriptorMap.putString("value", Base64.encodeToString(descriptor.getValue(), Base64.NO_WRAP));
						} else {
							descriptorMap.putString("value", null);
						}

						if (descriptor.getPermissions() > 0) {
							descriptorMap.putMap("permissions", Helper.decodePermissions(descriptor));
						}
						descriptorsArray.pushMap(descriptorMap);
					}
					if (descriptorsArray.size() > 0) {
						characteristicsMap.putArray("descriptors", descriptorsArray);
					}
					characteristicsArray.pushMap(characteristicsMap);
				}
				servicesArray.pushMap(serviceMap);
			}
			map.putArray("services", servicesArray);
			map.putArray("characteristics", characteristicsArray);
		}

		return map;
	}

	static WritableMap byteArrayToWritableMap(byte[] bytes) throws JSONException {
		WritableMap object = Arguments.createMap();
		object.putString("CDVType", "ArrayBuffer");
		object.putString("data", bytes != null ? Base64.encodeToString(bytes, Base64.NO_WRAP) : null);
		object.putArray("bytes", bytes != null ? BLEManager.bytesToWritableArray(bytes) : null);
		return object;
	}

	public boolean isConnected() {
		return connected;
	}

	public BluetoothDevice getDevice() {
		return device;
	}

	public Boolean hasService(UUID uuid) {
		if (gatt == null) {
			return null;
		}
		return gatt.getService(uuid) != null;
	}

	@Override
	public void onServicesDiscovered(BluetoothGatt gatt, int status) {
		super.onServicesDiscovered(gatt, status);
		if (retrieveServicesCallback != null) {
			WritableMap map = this.asWritableMap(gatt);
			retrieveServicesCallback.invoke(null, map);
			retrieveServicesCallback = null;
		}
	}

	@Override
	public void onConnectionStateChange(BluetoothGatt gatta, int status, int newState) {

		Log.d(BLEManager.LOG_TAG, "onConnectionStateChange to " + newState + " on peripheral: " + device.getAddress() + " with status" + status);

		this.gatt = gatta;

		if (newState == BluetoothProfile.STATE_CONNECTED) {

			connected = true;

			new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					try {
						gatt.discoverServices();
					} catch (NullPointerException e) {
						Log.d(BLEManager.LOG_TAG, "onConnectionStateChange connected but gatt of Run method was null");
					}
				}
			});

			sendConnectionEvent(device, "BLEManagerConnectPeripheral", status);

			if (connectCallback != null) {
				Log.d(BLEManager.LOG_TAG, "Connected to: " + device.getAddress());
				connectCallback.invoke();
				connectCallback = null;
			}

		} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

			if (connected) {
				connected = false;

				if (gatt != null) {
					gatt.disconnect();
					gatt.close();
					this.gatt = null;
				}
			}

			sendConnectionEvent(device, "BLEManagerDisconnectPeripheral", status);
			List<Callback> callbacks = Arrays.asList(writeCallback, retrieveServicesCallback, readRSSICallback, readCallback, registerNotifyCallback, requestMTUCallback);
			for (Callback currentCallback : callbacks) {
				if (currentCallback != null) {
					currentCallback.invoke("Device disconnected");
				}
			}
			if (connectCallback != null) {
				connectCallback.invoke("Connection error");
				connectCallback = null;
			}
			writeCallback = null;
			writeQueue.clear();
			readCallback = null;
			retrieveServicesCallback = null;
			readRSSICallback = null;
			registerNotifyCallback = null;
			requestMTUCallback = null;
		}

	}

	public void updateRssi(int rssi) {
		advertisingRSSI = rssi;
	}

	public void updateData(ScanRecord scanRecord) {
		advertisingData = scanRecord;
		advertisingDataBytes = scanRecord.getBytes();
	}

	public int unsignedToBytes(byte b) {
		return b & 0xFF;
	}

	@Override
	public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
		super.onCharacteristicChanged(gatt, characteristic);

		byte[] dataValue = characteristic.getValue();
		Log.d(BLEManager.LOG_TAG, "Read: " + BLEManager.bytesToHex(dataValue) + " from peripheral: " + device.getAddress());

		WritableMap map = Arguments.createMap();
		map.putString("peripheral", device.getAddress());
		map.putString("characteristic", characteristic.getUuid().toString());
		map.putString("service", characteristic.getService().getUuid().toString());
		map.putArray("value", BLEManager.bytesToWritableArray(dataValue));
		sendEvent("BLEManagerDidUpdateValueForCharacteristic", map);
	}

	@Override
	public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
		super.onCharacteristicRead(gatt, characteristic, status);
		Log.d(BLEManager.LOG_TAG, "onCharacteristicRead " + characteristic);

		if (readCallback != null) {

			if (status == BluetoothGatt.GATT_SUCCESS) {
				byte[] dataValue = characteristic.getValue();

				if (readCallback != null) {
					readCallback.invoke(null, BLEManager.bytesToWritableArray(dataValue));
				}
			} else {
				readCallback.invoke("Error reading " + characteristic.getUuid() + " status=" + status, null);
			}

			readCallback = null;

		}
	}

	@Override
	public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
		super.onCharacteristicWrite(gatt, characteristic, status);

		if (writeCallback != null) {

			if (writeQueue.size() > 0) {
				byte[] data = writeQueue.get(0);
				writeQueue.remove(0);
				doWrite(characteristic, data);
			} else {

				if (status == BluetoothGatt.GATT_SUCCESS) {
					writeCallback.invoke();
				} else {
					Log.e(BLEManager.LOG_TAG, "Error onCharacteristicWrite:" + status);
					writeCallback.invoke("Error writing status: " + status);
				}

				writeCallback = null;
			}
		} else {
			Log.e(BLEManager.LOG_TAG, "No callback on write");
		}
	}

	@Override
	public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
		super.onDescriptorWrite(gatt, descriptor, status);
		if (registerNotifyCallback != null) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				registerNotifyCallback.invoke();
				Log.d(BLEManager.LOG_TAG, "onDescriptorWrite success");
			} else {
				registerNotifyCallback.invoke("Error writing descriptor stats=" + status, null);
				Log.e(BLEManager.LOG_TAG, "Error writing descriptor stats=" + status);
			}

			registerNotifyCallback = null;
		} else {
			Log.e(BLEManager.LOG_TAG, "onDescriptorWrite with no callback");
		}
	}

	@Override
	public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
		super.onReadRemoteRssi(gatt, rssi, status);
		if (readRSSICallback != null) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				updateRssi(rssi);
				readRSSICallback.invoke(null, rssi);
			} else {
				readRSSICallback.invoke("Error reading RSSI status=" + status, null);
			}

			readRSSICallback = null;
		}
	}

	private void setNotify(UUID serviceUUID, UUID characteristicUUID, Boolean notify, Callback callback) {
		if (!isConnected()) {
			callback.invoke("Device is not connected", null);
			return;
		}
		Log.d(BLEManager.LOG_TAG, "setNotify");

		if (gatt == null) {
			callback.invoke("BluetoothGatt is null");
			return;
		}

		BluetoothGattService service = gatt.getService(serviceUUID);
		BluetoothGattCharacteristic characteristic = findNotifyCharacteristic(service, characteristicUUID);

		if (characteristic != null) {
			if (gatt.setCharacteristicNotification(characteristic, notify)) {

				BluetoothGattDescriptor descriptor = characteristic.getDescriptor(Helper.uuidFromString(CHARACTERISTIC_NOTIFICATION_CONFIG));
				if (descriptor != null) {

					// Prefer notify over indicate
					if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
						Log.d(BLEManager.LOG_TAG, "Characteristic " + characteristicUUID + " set NOTIFY");
						descriptor.setValue(notify ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
					} else if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
						Log.d(BLEManager.LOG_TAG, "Characteristic " + characteristicUUID + " set INDICATE");
						descriptor.setValue(notify ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
					} else {
						Log.d(BLEManager.LOG_TAG, "Characteristic " + characteristicUUID + " does not have NOTIFY or INDICATE property set");
					}

					try {
						registerNotifyCallback = callback;
						if (gatt.writeDescriptor(descriptor)) {
							Log.d(BLEManager.LOG_TAG, "setNotify complete");
						} else {
							registerNotifyCallback = null;
							callback.invoke("Failed to set client characteristic notification for " + characteristicUUID);
						}
					} catch (Exception e) {
						Log.d(BLEManager.LOG_TAG, "Error on setNotify", e);
						callback.invoke("Failed to set client characteristic notification for " + characteristicUUID + ", error: " + e.getMessage());
					}

				} else {
					callback.invoke("Set notification failed for " + characteristicUUID);
				}

			} else {
				callback.invoke("Failed to register notification for " + characteristicUUID);
			}

		} else {
			callback.invoke("Characteristic " + characteristicUUID + " not found");
		}

	}

	public void registerNotify(UUID serviceUUID, UUID characteristicUUID, Callback callback) {
		Log.d(BLEManager.LOG_TAG, "registerNotify");
		this.setNotify(serviceUUID, characteristicUUID, true, callback);
	}

	public void removeNotify(UUID serviceUUID, UUID characteristicUUID, Callback callback) {
		Log.d(BLEManager.LOG_TAG, "removeNotify");
		this.setNotify(serviceUUID, characteristicUUID, false, callback);
	}

	// Some devices reuse UUIDs across characteristics, so we can't use service.getCharacteristic(characteristicUUID)
	// instead check the UUID and properties for each characteristic in the service until we find the best match
	// This function prefers Notify over Indicate
	private BluetoothGattCharacteristic findNotifyCharacteristic(BluetoothGattService service, UUID characteristicUUID) {

		try {
			// Check for Notify first
			List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
			for (BluetoothGattCharacteristic characteristic : characteristics) {
				if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 && characteristicUUID.equals(characteristic.getUuid())) {
					return characteristic;
				}
			}

			// If there wasn't Notify Characteristic, check for Indicate
			for (BluetoothGattCharacteristic characteristic : characteristics) {
				if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 && characteristicUUID.equals(characteristic.getUuid())) {
					return characteristic;
				}
			}

			// As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
			return service.getCharacteristic(characteristicUUID);
		} catch (Exception e) {
			Log.e(BLEManager.LOG_TAG, "Errore su caratteristica " + characteristicUUID, e);
			return null;
		}
	}

	public void read(UUID serviceUUID, UUID characteristicUUID, Callback callback) {

		if (!isConnected()) {
			callback.invoke("Device is not connected", null);
			return;
		}
		if (gatt == null) {
			callback.invoke("BluetoothGatt is null", null);
			return;
		}

		BluetoothGattService service = gatt.getService(serviceUUID);
		BluetoothGattCharacteristic characteristic = findReadableCharacteristic(service, characteristicUUID);

		if (characteristic == null) {
			callback.invoke("Characteristic " + characteristicUUID + " not found.", null);
		} else {
			readCallback = callback;
			if (!gatt.readCharacteristic(characteristic)) {
				readCallback = null;
				callback.invoke("Read failed", null);
			}
		}
	}

	public void readRSSI(Callback callback) {
		if (!isConnected()) {
			callback.invoke("Device is not connected", null);
			return;
		}
		if (gatt == null) {
			callback.invoke("BluetoothGatt is null", null);
			return;
		}

		readRSSICallback = callback;

		if (!gatt.readRemoteRssi()) {
			readCallback = null;
			callback.invoke("Read RSSI failed", null);
		}
	}

	public void refreshCache(Callback callback) {
		try {
			Method localMethod = gatt.getClass().getMethod("refresh", new Class[0]);
			if (localMethod != null) {
				boolean res = ((Boolean) localMethod.invoke(gatt, new Object[0])).booleanValue();
				callback.invoke(null, res);
			} else {
				callback.invoke("Could not refresh cache for device.");
			}
		} catch (Exception localException) {
			Log.e(TAG, "An exception occured while refreshing device");
			callback.invoke(localException.getMessage());
		}
	}

	public void retrieveServices(Callback callback) {
		if (!isConnected()) {
			callback.invoke("Device is not connected", null);
			return;
		}
		if (gatt == null) {
			callback.invoke("BluetoothGatt is null", null);
			return;
		}
		this.retrieveServicesCallback = callback;
		gatt.discoverServices();
	}

	// Some peripherals re-use UUIDs for multiple characteristics so we need to check the properties
	// and UUID of all characteristics instead of using service.getCharacteristic(characteristicUUID)
	private BluetoothGattCharacteristic findReadableCharacteristic(BluetoothGattService service, UUID characteristicUUID) {

		if (service != null) {
			int read = BluetoothGattCharacteristic.PROPERTY_READ;

			List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
			for (BluetoothGattCharacteristic characteristic : characteristics) {
				if ((characteristic.getProperties() & read) != 0 && characteristicUUID.equals(characteristic.getUuid())) {
					return characteristic;
				}
			}

			// As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
			return service.getCharacteristic(characteristicUUID);
		}

		return null;
	}

	public boolean doWrite(BluetoothGattCharacteristic characteristic, byte[] data) {
		characteristic.setValue(data);

		if (!gatt.writeCharacteristic(characteristic)) {
			Log.d(BLEManager.LOG_TAG, "Error on doWrite");
			return false;
		}
		return true;
	}

	public void write(UUID serviceUUID, UUID characteristicUUID, byte[] data, Integer maxByteSize, Integer queueSleepTime, Callback callback, int writeType) {
		if (!isConnected()) {
			callback.invoke("Device is not connected", null);
			return;
		}
		if (gatt == null) {
			callback.invoke("BluetoothGatt is null");
		} else {
			BluetoothGattService service = gatt.getService(serviceUUID);
			BluetoothGattCharacteristic characteristic = findWritableCharacteristic(service, characteristicUUID, writeType);

			if (characteristic == null) {
				callback.invoke("Characteristic " + characteristicUUID + " not found.");
			} else {
				characteristic.setWriteType(writeType);

				if (writeQueue.size() > 0) {
					callback.invoke("You have already an queued message");
				}

				if (writeCallback != null) {
					callback.invoke("You're already writing");
				}

				if (writeQueue.size() == 0 && writeCallback == null) {

					if (BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT == writeType) {
						writeCallback = callback;
					}

					if (data.length > maxByteSize) {
						int dataLength = data.length;
						int count = 0;
						byte[] firstMessage = null;
						List<byte[]> splittedMessage = new ArrayList<>();

						while (count < dataLength && (dataLength - count > maxByteSize)) {
							if (count == 0) {
								firstMessage = Arrays.copyOfRange(data, count, count + maxByteSize);
							} else {
								byte[] splitMessage = Arrays.copyOfRange(data, count, count + maxByteSize);
								splittedMessage.add(splitMessage);
							}
							count += maxByteSize;
						}
						if (count < dataLength) {
							// Other bytes in queue
							byte[] splitMessage = Arrays.copyOfRange(data, count, data.length);
							splittedMessage.add(splitMessage);
						}

						if (BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT == writeType) {
							writeQueue.addAll(splittedMessage);
							if (!doWrite(characteristic, firstMessage)) {
								writeQueue.clear();
								writeCallback = null;
								callback.invoke("Write failed");
							}
						} else {
							try {
								boolean writeError = false;
								if (!doWrite(characteristic, firstMessage)) {
									writeError = true;
									callback.invoke("Write failed");
								}
								if (!writeError) {
									Thread.sleep(queueSleepTime);
									for (byte[] message : splittedMessage) {
										if (!doWrite(characteristic, message)) {
											writeError = true;
											callback.invoke("Write failed");
											break;
										}
										Thread.sleep(queueSleepTime);
									}
									if (!writeError) {
										callback.invoke();
									}
								}
							} catch (InterruptedException e) {
								callback.invoke("Error during writing");
							}
						}
					} else if (doWrite(characteristic, data)) {
						Log.d(BLEManager.LOG_TAG, "Write completed");
						if (BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE == writeType) {
							callback.invoke();
						}
					} else {
						callback.invoke("Write failed");
						writeCallback = null;
					}
				}
			}
		}
	}

	public void requestConnectionPriority(int connectionPriority, Callback callback) {
		if (gatt == null) {
			callback.invoke("BluetoothGatt is null", null);
			return;
		}

		if (Build.VERSION.SDK_INT >= LOLLIPOP) {
			boolean status = gatt.requestConnectionPriority(connectionPriority);
			callback.invoke(null, status);
		} else {
			callback.invoke("Requesting connection priority requires at least API level 21", null);
		}
	}

	public void requestMTU(int mtu, Callback callback) {
		if (!isConnected()) {
			callback.invoke("Device is not connected", null);
			return;
		}

		if (gatt == null) {
			callback.invoke("BluetoothGatt is null", null);
			return;
		}

		if (Build.VERSION.SDK_INT >= LOLLIPOP) {
			requestMTUCallback = callback;
			gatt.requestMtu(mtu);
		} else {
			callback.invoke("Requesting MTU requires at least API level 21", null);
		}
	}

	@Override
	public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
		super.onMtuChanged(gatt, mtu, status);
		if (requestMTUCallback != null) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				requestMTUCallback.invoke(null, mtu);
			} else {
				requestMTUCallback.invoke("Error requesting MTU status = " + status, null);
			}

			requestMTUCallback = null;
		}
	}

	// Some peripherals re-use UUIDs for multiple characteristics so we need to check the properties
	// and UUID of all characteristics instead of using service.getCharacteristic(characteristicUUID)
	private BluetoothGattCharacteristic findWritableCharacteristic(BluetoothGattService service, UUID characteristicUUID, int writeType) {
		try {
			// get write property
			int writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE;
			if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
				writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
			}

			List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
			for (BluetoothGattCharacteristic characteristic : characteristics) {
				if ((characteristic.getProperties() & writeProperty) != 0 && characteristicUUID.equals(characteristic.getUuid())) {
					return characteristic;
				}
			}

			// As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
			return service.getCharacteristic(characteristicUUID);
		} catch (Exception e) {
			Log.e(BLEManager.LOG_TAG, "Error on findWritableCharacteristic", e);
			return null;
		}
	}

	private String generateHashKey(BluetoothGattCharacteristic characteristic) {
		return generateHashKey(characteristic.getService().getUuid(), characteristic);
	}

	private String generateHashKey(UUID serviceUUID, BluetoothGattCharacteristic characteristic) {
		return String.valueOf(serviceUUID) + "|" + characteristic.getUuid() + "|" + characteristic.getInstanceId();
	}

}
