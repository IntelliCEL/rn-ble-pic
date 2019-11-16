//
//  BLEManager.swift
//  RNBlePic
//
//  Created by MAZ on 08/11/2019.
//  Copyright © 2019 Facebook. All rights reserved.
//

import Foundation
import CoreBluetooth

@objc(BLEManager)
class BLEManager: RCTEventEmitter {
    
    var bleAdvertiser: BLEAdvertiser!
    var hasListeners: Bool = false
    var isInitialized: Bool = false
    var LOG_TAG: String = "BLE_PIC"
    var manager: CBPeripheralManager!
    var startCallback: RCTResponseSenderBlock?
    
    override init() {
        super.init()
//        manager = CBPeripheralManager(delegate: CBPeripheralManager(), queue: nil, options: nil)
    }
    
    @objc override func supportedEvents() -> [String]! { return ["onWarning"] }
    override func startObserving() { hasListeners = true }
    override func stopObserving() { hasListeners = false }
    @objc override static func requiresMainQueueSetup() -> Bool { return false }
    
    // Recat Methods
    @objc func `init`(_ callback : @escaping RCTResponseSenderBlock) {
//        if (manager == nil) {
//            printJS("BLEPIC failed to initialize because bluetooth is not supported.");
//			callback(["BLEPIC failed to initialize because bluetooth is not supported."])
//            return;
//        }
        
        bleAdvertiser = BLEAdvertiser(pBleManager: self);
		self.isInitialized = true;
		callback([NSNull()])
    }

    @objc func hasInitialized() -> Bool {
        return self.isInitialized;
    }
    
    /** Advertiser Methods Start */
    @objc func setPeripheralName(_ name: String) {
        if(self.isInitialized) {
            bleAdvertiser.setPeripheralName(name: name)
        }
    }
    
    @objc(addService:primary:)
    func addService(_ uuid: String, primary: Bool) {
        if(self.isInitialized) {
            bleAdvertiser.addService(uuid: uuid, primary: primary);
        }
    }
    
    @objc(addCharacteristicToService:uuid:permissions:properties:data:)
    func addCharacteristicToService(_ serviceUUID: String, characteristicUUID: String, permissions: UInt, properties: UInt, characteristicData: String) {
        if(self.isInitialized) {
            bleAdvertiser.addCharacteristicToService(serviceUUID:serviceUUID, characteristicUUID:characteristicUUID, permissions:permissions, properties:properties, characteristicData:characteristicData);
        }
    }
    
    @objc func startAdvertising(_ callback : @escaping RCTResponseSenderBlock) {
        if(self.isInitialized) {
            bleAdvertiser.startAdvertising(callback: callback);
        }
    }
    
    @objc func isAdvertising() -> Bool {
        if(self.isInitialized) {
            return bleAdvertiser.isAdvertising();
        }
        return false;
    }
    
    @objc func stopAdvertising() {
        if(self.isInitialized) {
            bleAdvertiser.stopAdvertising();
        }
    }
    
    /** Advertiser Methods End */

    func printJS(_ message: Any) {
        print("\(LOG_TAG) \(message)");
        if(hasListeners) {
            sendEvent(withName: "onWarning", body: message)
        }
    }
}
