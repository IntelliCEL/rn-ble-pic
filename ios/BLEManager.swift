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
class BLEManager: RCTEventEmitter, CBPeripheralManagerDelegate {
    var advertising: Bool = false
    var hasListeners: Bool = false
    var isInitialized: Bool = false
    var name: String = "BLE_PIC"
    var LOG_TAG: String = "BLE_PIC"
    var servicesMap = Dictionary<String, CBMutableService>()
    var manager: CBPeripheralManager!
    var startCallback: RCTResponseSenderBlock?
    
    override init() {
        super.init()
        manager = CBPeripheralManager(delegate: self, queue: nil, options: nil)
    }
    
    //// PUBLIC METHODS
    @objc func `init`(_ callback : @escaping RCTResponseSenderBlock) {
        if (manager != nil) {
            printJS("BLEPIC failed to initialize because bluetooth is not supported.");
			callback(["BLEPIC failed to initialize because bluetooth is not supported."])
            return;
        }

		self.isInitialized = true;
		callback([NSNull()])
    }
    
    @objc func setName(_ name: String) {
        self.name = name
        printJS("name set to \(name)")
    }
    
    @objc func isAdvertising(_ resolve: RCTPromiseResolveBlock, rejecter reject: RCTPromiseRejectBlock) {
        resolve(advertising)
        printJS("called isAdvertising")
    }
    
    @objc(addService:primary:)
    func addService(_ uuid: String, primary: Bool) {
        let serviceUUID = CBUUID(string: uuid)
        let service = CBMutableService(type: serviceUUID, primary: primary)
        if(servicesMap.keys.contains(uuid) != true){
            servicesMap[uuid] = service
            // manager.add(service)
            printJS("added service \(uuid)")
        }
        else {
            printJS("service \(uuid) already there")
        }
    }
    
    @objc(addCharacteristicToService:uuid:permissions:properties:data:)
    func addCharacteristicToService(_ serviceUUID: String, uuid: String, permissions: UInt, properties: UInt, data: String) {
        let characteristicUUID = CBUUID(string: uuid)
        let propertyValue = CBCharacteristicProperties(rawValue: properties)
        let permissionValue = CBAttributePermissions(rawValue: permissions)
        let byteData: Data = data.data(using: .utf8)!
        let characteristic = CBMutableCharacteristic( type: characteristicUUID, properties: propertyValue, value: byteData, permissions: permissionValue)
        if(servicesMap[serviceUUID] != nil) {
            if(servicesMap[serviceUUID]!.characteristics != nil) {
                servicesMap[serviceUUID]!.characteristics!.append(characteristic)
            } else {
                servicesMap[serviceUUID]!.characteristics = [characteristic]
            }
        }
        servicesMap[serviceUUID]?.characteristics?.append(characteristic)
        printJS("added characteristic to service \(servicesMap[serviceUUID])")
    }
    
    @objc func startAdvertising(_ callback : @escaping RCTResponseSenderBlock) {
        if (manager.state != .poweredOn) {
            printJS("Bluetooth turned off")
            return;
        }
        
        startCallback = callback;

        let advertisementData = [
            CBAdvertisementDataLocalNameKey: name,
            CBAdvertisementDataServiceUUIDsKey: getServiceUUIDArray()
            ] as [String : Any]

        
        for (uuid, service) in servicesMap {
            manager.add(service)
        }
        manager.startAdvertising(advertisementData)
    }
    
    @objc func stopAdvertising() {
        manager.stopAdvertising()
        advertising = false
        printJS("called stop")
    }

    @objc(sendNotificationToDevices:characteristicUUID:data:)
    func sendNotificationToDevices(_ serviceUUID: String, characteristicUUID: String, data: Data) {
        if(servicesMap.keys.contains(serviceUUID) == true){
            let service = servicesMap[serviceUUID]!
            let characteristic = getCharacteristicForService(service, characteristicUUID)
            if (characteristic == nil) { printJS("service \(serviceUUID) does NOT have characteristic \(characteristicUUID)") }

            let char = characteristic as! CBMutableCharacteristic
            char.value = data
            let success = manager.updateValue( data, for: char, onSubscribedCentrals: nil)
            if (success){
                printJS("changed data for characteristic \(characteristicUUID)")
            } else {
                printJS("failed to send changed data for characteristic \(characteristicUUID)")
            }

        } else {
            printJS("service \(serviceUUID) does not exist")
        }
    }
    
    //// EVENTS
    // Respond to Read request
    func peripheralManager(peripheral: CBPeripheralManager, didReceiveReadRequest request: CBATTRequest)
    {
        let characteristic = getCharacteristic(request.characteristic.uuid)
        if (characteristic != nil){
            
            request.value = characteristic?.value
            manager.respond(to: request, withResult: .success)
            printJS("characteristics \(request.value)")
        } else {
            printJS("cannot read, characteristic not found")
        }
    }

    // Respond to Write request
    func peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests requests: [CBATTRequest])
    {
        for request in requests
        {
            let characteristic = getCharacteristic(request.characteristic.uuid)
            if (characteristic == nil) { printJS("characteristic for writing not found") }
            if request.characteristic.uuid.isEqual(characteristic?.uuid)
            {
                let char = characteristic as! CBMutableCharacteristic
                char.value = request.value
            } else {
                printJS("characteristic you are trying to access doesn't match")
            }
        }
        manager.respond(to: requests[0], withResult: .success)
    }

    // Respond to Subscription to Notification events
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        let char = characteristic as! CBMutableCharacteristic
        printJS("subscribed centrals: \(String(describing: char.subscribedCentrals))")
    }

    // Respond to Unsubscribe events
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        let char = characteristic as! CBMutableCharacteristic
        printJS("unsubscribed centrals: \(String(describing: char.subscribedCentrals))")
    }

    // Service added
    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error = error {
            printJS("error: \(error)")
            return
        }
        printJS("service: \(service)")
    }

    // Bluetooth status changed
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        var state: Any
        if #available(iOS 10.0, *) {
            state = peripheral.state.description
        } else {
            state = peripheral.state
        }
        printJS("BT state change: \(state)")
    }

    // Advertising started
    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            advertising = false
            printJS("Advertising onStartFailure: \(error)");
			startCallback!(["Advertising onStartFailure: \(error)"])

            return
        }
        advertising = true
        startCallback!([NSNull()])
    }
    
    //// HELPERS
    func getCharacteristic(_ characteristicUUID: CBUUID) -> CBCharacteristic? {
        for (uuid, service) in servicesMap {
            for characteristic in service.characteristics ?? [] {
                if (characteristic.uuid.isEqual(characteristicUUID) ) {
                    printJS("service \(uuid) does have characteristic \(characteristicUUID)")
                    if (characteristic is CBMutableCharacteristic) {
                        return characteristic
                    }
                    printJS("but it is not mutable")
                } else {
                    printJS("characteristic you are trying to access doesn't match")
                }
            }
        }
        return nil
    }

    func getCharacteristicForService(_ service: CBMutableService, _ characteristicUUID: String) -> CBCharacteristic? {
        for characteristic in service.characteristics ?? [] {
            if (characteristic.uuid.isEqual(characteristicUUID) ) {
                printJS("service \(service.uuid) does have characteristic \(characteristicUUID)")
                if (characteristic is CBMutableCharacteristic) {
                    return characteristic
                }
                printJS("but it is not mutable")
            } else {
                printJS("characteristic you are trying to access doesn't match")
            }
        }
        return nil
    }

    func getServiceUUIDArray() -> Array<CBUUID> {
        var serviceArray = [CBUUID]()
        for (_, service) in servicesMap {
            serviceArray.append(service.uuid)
        }
        return serviceArray
    }

    func printJS(_ message: Any) {
        print("\(LOG_TAG) \(message)");
        if(hasListeners) {
            sendEvent(withName: "onWarning", body: message)
        }
    }

    @objc override func supportedEvents() -> [String]! { return ["onWarning"] }
    override func startObserving() { hasListeners = true }
    override func stopObserving() { hasListeners = false }
    @objc override static func requiresMainQueueSetup() -> Bool { return false }
    
}

@available(iOS 10.0, *)
extension CBManagerState: CustomStringConvertible {
    public var description: String {
        switch self {
        case .poweredOff: return ".poweredOff"
        case .poweredOn: return ".poweredOn"
        case .resetting: return ".resetting"
        case .unauthorized: return ".unauthorized"
        case .unknown: return ".unknown"
        case .unsupported: return ".unsupported"
        }
    }
}
