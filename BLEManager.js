"use strict";
var React = require("react-native");
var bleManager = React.NativeModules.BLEManager;

class BLEManager {
  constructor() {}

  setPeripheralName(name) {
    return bleManager.setPeripheralName(name);
  }

  addService(serviceUUID, primary, serviceData = null) {
    return bleManager.addService(serviceUUID, primary, serviceData);
  }

  addCharacteristicToService(
    serviceUUID,
    charUUID,
    permissions,
    properties,
    data = null
  ) {
    return bleManager.addCharacteristicToService(
      serviceUUID,
      charUUID,
      permissions,
      properties,
      data
    );
  }

  startAdvertising() {
    return new Promise((fulfill, reject) => {
      bleManager.startAdvertising(error => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  isAdvertising() {
    return bleManager.isAdvertising();
  }

  stopAdvertising() {
    return bleManager.stopAdvertising();
  }
}

module.exports = new BLEManager();
