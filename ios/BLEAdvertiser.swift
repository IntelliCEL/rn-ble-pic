//
//  BLEAdvertiser.swift
//  RNBlePic
//
//  Created by MAZ on 16/11/2019.
//  Copyright © 2019 Facebook. All rights reserved.
//

import Foundation
import CoreBluetooth

class BLEAdvertiser: NSObject, CBPeripheralManagerDelegate {
    
    var peripheralName: String?
    var servicesMap = Dictionary<String, CBMutableService>()
    var manager: CBPeripheralManager!
    var advertising: Bool = false
    var bleManager: BLEManager!
    
    var hasListeners: Bool = false
    var startCallback: RCTResponseSenderBlock?
    
    init(pBleManager: BLEManager) {
        super.init()
        bleManager = pBleManager;
        manager = CBPeripheralManager(delegate: self, queue: nil, options: nil)
        advertising = false
        peripheralName = nil
    }
    
    func setPeripheralName(name: String) {
        self.peripheralName = name
    }
    
    func addService(uuid: String, primary: Bool) {
        let serviceUUID = CBUUID(string: uuid)
        let service = CBMutableService(type: serviceUUID, primary: primary)
        if(servicesMap.keys.contains(uuid) != true){
            servicesMap[uuid] = service
        }
    }
    
    func addCharacteristicToService(serviceUUID: String, characteristicUUID: String, permissions: UInt, properties: UInt, characteristicData: String) {
        let characteristicUUID = CBUUID(string: characteristicUUID)
        let propertyValue = CBCharacteristicProperties(rawValue: properties)
        let permissionValue = CBAttributePermissions(rawValue: permissions)
        let byteData: Data = characteristicData.data(using: .utf8)!
        let characteristic = CBMutableCharacteristic( type: characteristicUUID, properties: propertyValue, value: byteData, permissions: permissionValue)
        if(servicesMap[serviceUUID] != nil) {
            if(servicesMap[serviceUUID]!.characteristics != nil) {
                servicesMap[serviceUUID]!.characteristics!.append(characteristic)
            } else {
                servicesMap[serviceUUID]!.characteristics = [characteristic]
            }
        }
    }
    
    func startAdvertising(callback : @escaping RCTResponseSenderBlock) {
        if (manager.state != .poweredOn) {
            bleManager.printJS("Bluetooth not supported or not enabled.");
            callback(["Bluetooth not supported or not enabled."])
            return;
        }
        
        startCallback = callback;
        
        let advertisementData: [String:Any]
        
        if(self.peripheralName != nil) {
            advertisementData = [
                    CBAdvertisementDataLocalNameKey: self.peripheralName as Any,
                    CBAdvertisementDataServiceUUIDsKey: getServiceUUIDArray()
                ]
        } else {
            advertisementData = [CBAdvertisementDataServiceUUIDsKey: getServiceUUIDArray()]
        }
        
        for (_, service) in servicesMap {
            manager.add(service)
        }
        manager.startAdvertising(advertisementData)
    }
    
    func isAdvertising() -> Bool {
        return advertising
    }
    
    func stopAdvertising() {
        manager.stopAdvertising()
        advertising = false
    }
    
    //// EVENTS
    // Respond to Read request
    func peripheralManager(peripheral: CBPeripheralManager, didReceiveReadRequest request: CBATTRequest)
    {
        let characteristic = getCharacteristic(request.characteristic.uuid)
        if (characteristic != nil){
            request.value = characteristic?.value
            manager.respond(to: request, withResult: .success)
            bleManager.printJS("characteristics \(request.value)")
        } else {
            bleManager.printJS("cannot read, characteristic not found")
        }
    }

    // Respond to Write request
    func peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests requests: [CBATTRequest])
    {
        for request in requests
        {
            let characteristic = getCharacteristic(request.characteristic.uuid)
            if (characteristic == nil) { bleManager.printJS("characteristic for writing not found") }
            if request.characteristic.uuid.isEqual(characteristic?.uuid)
            {
                let char = characteristic as! CBMutableCharacteristic
                char.value = request.value
            } else {
                bleManager.printJS("characteristic you are trying to access doesn't match")
            }
        }
        manager.respond(to: requests[0], withResult: .success)
    }

    // Respond to Subscription to Notification events
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        let char = characteristic as! CBMutableCharacteristic
        bleManager.printJS("subscribed centrals: \(String(describing: char.subscribedCentrals))")
    }

    // Respond to Unsubscribe events
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        let char = characteristic as! CBMutableCharacteristic
        bleManager.printJS("unsubscribed centrals: \(String(describing: char.subscribedCentrals))")
    }

    // Service added
    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error = error {
            bleManager.printJS("error: \(error)")
            return
        }
        bleManager.printJS("service: \(service)")
    }

    // Bluetooth status changed
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        var state: Any
        if #available(iOS 10.0, *) {
            state = peripheral.state.description
        } else {
            state = peripheral.state
        }
        bleManager.printJS("BT state change: \(state)")
    }

    // Advertising started
    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            advertising = false
            bleManager.printJS("Advertising onStartFailure: \(error)")
            startCallback!(["Advertising onStartFailure: \(error)"])
            return
        }
        bleManager.printJS("Advert Started")
        advertising = true
        startCallback!([NSNull()])
    }
    
    //// HELPERS
    func getCharacteristic(_ characteristicUUID: CBUUID) -> CBCharacteristic? {
        for (uuid, service) in servicesMap {
            for characteristic in service.characteristics ?? [] {
                if (characteristic.uuid.isEqual(characteristicUUID) ) {
                    bleManager.printJS("service \(uuid) does have characteristic \(characteristicUUID)")
                    if (characteristic is CBMutableCharacteristic) {
                        return characteristic
                    }
                    bleManager.printJS("but it is not mutable")
                } else {
                    bleManager.printJS("characteristic you are trying to access doesn't match")
                }
            }
        }
        return nil
    }

    func getCharacteristicForService(_ service: CBMutableService, _ characteristicUUID: String) -> CBCharacteristic? {
        for characteristic in service.characteristics ?? [] {
            if (characteristic.uuid.isEqual(characteristicUUID) ) {
                bleManager.printJS("service \(service.uuid) does have characteristic \(characteristicUUID)")
                if (characteristic is CBMutableCharacteristic) {
                    return characteristic
                }
                bleManager.printJS("but it is not mutable")
            } else {
                bleManager.printJS("characteristic you are trying to access doesn't match")
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
