"use strict";
var React = require("react-native");
var bleManager = React.NativeModules.BLEManager;

class BLEManager {
  constructor() {
    this.isPeripheralConnected = this.isPeripheralConnected.bind(this);
  }

  init() {
    return new Promise((fulfill, reject) => {
      bleManager.init(error => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  hasInitialized() {
    return bleManager.hasInitialized();
  }

  enableBluetooth() {
    return new Promise((fulfill, reject) => {
      bleManager.enableBluetooth(error => {
        if (error != null) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  checkState() {
    bleManager.checkState();
  }

  /** BLE ADVERTISING SECTION START */

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

  /** BLE ADVERTISING SECTION END */

  /** BLE SCANNING SECTION START */

  startScan(serviceUUIDs, seconds, scanningOptions = {}) {
    return new Promise((fulfill, reject) => {
      // (ANDROID) Match as many advertisement per filter as hw could allow
      // dependes on current capability and availability of the resources in hw.
      if (scanningOptions.numberOfMatches == null) {
        scanningOptions.numberOfMatches = 3;
      }

      // (ANDROID) Defaults to MATCH_MODE_AGGRESSIVE
      if (scanningOptions.matchMode == null) {
        scanningOptions.matchMode = 1;
      }

      // (ANDROID) Defaults to SCAN_MODE_LOW_POWER on android
      if (scanningOptions.scanMode == null) {
        scanningOptions.scanMode = 0;
      }

      bleManager.startScan(serviceUUIDs, seconds, scanningOptions, error => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  stopScan() {
    return new Promise((fulfill, reject) => {
      bleManager.stopScan(error => {
        if (error != null) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  connect(peripheralId) {
    return new Promise((fulfill, reject) => {
      bleManager.connect(peripheralId, error => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  disconnect(peripheralId, force = true) {
    return new Promise((fulfill, reject) => {
      bleManager.disconnect(peripheralId, force, error => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  retrieveServices(peripheralId, services) {
    return new Promise((fulfill, reject) => {
      bleManager.retrieveServices(
        peripheralId,
        services,
        (error, peripheral) => {
          if (error) {
            reject(error);
          } else {
            fulfill(peripheral);
          }
        }
      );
    });
  }

  startNotification(peripheralId, serviceUUID, characteristicUUID) {
    return new Promise((fulfill, reject) => {
      bleManager.startNotification(
        peripheralId,
        serviceUUID,
        characteristicUUID,
        error => {
          if (error) {
            reject(error);
          } else {
            fulfill();
          }
        }
      );
    });
  }

  stopNotification(peripheralId, serviceUUID, characteristicUUID) {
    return new Promise((fulfill, reject) => {
      bleManager.stopNotification(
        peripheralId,
        serviceUUID,
        characteristicUUID,
        error => {
          if (error) {
            reject(error);
          } else {
            fulfill();
          }
        }
      );
    });
  }

  write(peripheralId, serviceUUID, characteristicUUID, data, maxByteSize) {
    if (maxByteSize == null) {
      maxByteSize = 20;
    }
    return new Promise((fulfill, reject) => {
      bleManager.write(
        peripheralId,
        serviceUUID,
        characteristicUUID,
        data,
        maxByteSize,
        error => {
          if (error) {
            reject(error);
          } else {
            fulfill();
          }
        }
      );
    });
  }

  writeWithoutResponse(
    peripheralId,
    serviceUUID,
    characteristicUUID,
    data,
    maxByteSize,
    queueSleepTime
  ) {
    if (maxByteSize == null) {
      maxByteSize = 20;
    }
    if (queueSleepTime == null) {
      queueSleepTime = 10;
    }
    return new Promise((fulfill, reject) => {
      bleManager.writeWithoutResponse(
        peripheralId,
        serviceUUID,
        characteristicUUID,
        data,
        maxByteSize,
        queueSleepTime,
        error => {
          if (error) {
            reject(error);
          } else {
            fulfill();
          }
        }
      );
    });
  }

  read(peripheralId, serviceUUID, characteristicUUID) {
    return new Promise((fulfill, reject) => {
      bleManager.read(
        peripheralId,
        serviceUUID,
        characteristicUUID,
        (error, data) => {
          if (error) {
            reject(error);
          } else {
            fulfill(data);
          }
        }
      );
    });
  }

  getDiscoveredPeripherals() {
    return new Promise((fulfill, reject) => {
      bleManager.getDiscoveredPeripherals((error, result) => {
        if (error) {
          reject(error);
        } else {
          if (result != null) {
            fulfill(result);
          } else {
            fulfill([]);
          }
        }
      });
    });
  }

  getConnectedPeripherals(serviceUUIDs) {
    return new Promise((fulfill, reject) => {
      bleManager.getConnectedPeripherals(serviceUUIDs, (error, result) => {
        if (error) {
          reject(error);
        } else {
          if (result != null) {
            fulfill(result);
          } else {
            fulfill([]);
          }
        }
      });
    });
  }

  removePeripheral(peripheralId) {
    return new Promise((fulfill, reject) => {
      bleManager.removePeripheral(peripheralId, error => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  isPeripheralConnected(peripheralId, serviceUUIDs) {
    return this.getConnectedPeripherals(serviceUUIDs).then(result => {
      if (
        result.find(p => {
          return p.id === peripheralId;
        })
      ) {
        return true;
      } else {
        return false;
      }
    });
  }

  /** BLE SCANNING SECTION END */
}

module.exports = new BLEManager();
