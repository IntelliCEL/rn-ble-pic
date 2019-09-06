package com.blepic;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONArray;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Helper {

	public static final String UUID_BASE = "0000XXXX-0000-1000-8000-00805f9b34fb";

	public static WritableMap decodeProperties(BluetoothGattCharacteristic characteristic) {

		WritableMap props = Arguments.createMap();
		int properties = characteristic.getProperties();

		if ((properties & BluetoothGattCharacteristic.PROPERTY_BROADCAST) != 0x0 ) {
			props.putString("Broadcast", "Broadcast");
		}

		if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0x0 ) {
			props.putString("Read", "Read");
		}

		if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0x0 ) {
			props.putString("WriteWithoutResponse", "WriteWithoutResponse");
		}

		if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0x0 ) {
			props.putString("Write", "Write");
		}

		if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0x0 ) {
			props.putString("Notify", "Notify");
		}

		if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0x0 ) {
			props.putString("Indicate", "Indicate");
		}

		if ((properties & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0x0 ) {
			// Android calls this "write with signature", using iOS name for now
			props.putString("AuthenticateSignedWrites", "AuthenticateSignedWrites");
		}

		if ((properties & BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) != 0x0 ) {
			props.putString("ExtendedProperties", "ExtendedProperties");
		}
		return props;
	}

	public static WritableMap decodePermissions(BluetoothGattCharacteristic characteristic) {

		WritableMap props = Arguments.createMap();
		int permissions = characteristic.getPermissions();

		if ((permissions & BluetoothGattCharacteristic.PERMISSION_READ) != 0x0 ) {
			props.putString("Read", "Read");
		}

		if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE) != 0x0 ) {
			props.putString("Write", "Write");
		}

		if ((permissions & BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED) != 0x0 ) {
			props.putString("ReadEncrypted", "ReadEncrypted");
		}

		if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED) != 0x0 ) {
			props.putString("WriteEncrypted", "WriteEncrypted");
		}

		if ((permissions & BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM) != 0x0 ) {
			props.putString("ReadEncryptedMITM", "ReadEncryptedMITM");
		}

		if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM) != 0x0 ) {
			props.putString("WriteEncryptedMITM", "WriteEncryptedMITM");
		}

		if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED) != 0x0 ) {
			props.putString("WriteSigned", "WriteSigned");
		}

		if ((permissions & BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM) != 0x0 ) {
			props.putString("WriteSignedMITM", "WriteSignedMITM");
		}

		return props;
	}

	public static WritableMap decodePermissions(BluetoothGattDescriptor descriptor) {

		WritableMap props = Arguments.createMap();
		int permissions = descriptor.getPermissions();

		if ((permissions & BluetoothGattDescriptor.PERMISSION_READ) != 0x0 ) {
			props.putString("Read", "Read");
		}

		if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE) != 0x0 ) {
			props.putString("Write", "Write");
		}

		if ((permissions & BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED) != 0x0 ) {
			props.putString("ReadEncrypted", "ReadEncrypted");
		}

		if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED) != 0x0 ) {
			props.putString("WriteEncrypted", "WriteEncrypted");
		}

		if ((permissions & BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM) != 0x0 ) {
			props.putString("ReadEncryptedMITM", "ReadEncryptedMITM");
		}

		if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM) != 0x0 ) {
			props.putString("WriteEncryptedMITM", "WriteEncryptedMITM");
		}

		if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED) != 0x0 ) {
			props.putString("WriteSigned", "WriteSigned");
		}

		if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED_MITM) != 0x0 ) {
			props.putString("WriteSignedMITM", "WriteSignedMITM");
		}

		return props;
	}

	public static UUID uuidFromString(String uuid) {
		if (uuid.length() == 4) {
			uuid = UUID_BASE.replace("XXXX", uuid);
		}
		return UUID.fromString(uuid);
	}

	public static String uuidToString(UUID uuid) {
		String longUUID = uuid.toString();
		Pattern pattern = Pattern.compile("0000(.{4})-0000-1000-8000-00805f9b34fb", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(longUUID);
		if (matcher.matches()) {
			// 16 bit UUID
			return matcher.group(1);
		} else {
			return longUUID;
		}
	}
}