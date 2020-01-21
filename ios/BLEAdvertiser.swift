//
//  BLEAdvertiser.swift
//  RNBlePic
//
//  Created by MAZ on 16/11/2019.
//  Copyright © 2019 Facebook. All rights reserved.
//

import Foundation
import CoreBluetooth

@objc public class BLEAdvertiser: NSObject, CBPeripheralManagerDelegate {
    
    var peripheralName: String?
    var servicesMap = Dictionary<String, CBMutableService>()
    var manager: CBPeripheralManager!
    var advertising: Bool = false
    var bleManager: BLEManager!
    
    var hasListeners: Bool = false
    var startCallback: RCTResponseSenderBlock?
    
    @objc override init() {
        super.init()
    }
    
//    @objc(initialize:)
    @objc func initialize(_ pBleManager: BLEManager) {
        bleManager = pBleManager;
        manager = CBPeripheralManager(delegate: self, queue: nil, options: nil)
        advertising = false
        peripheralName = nil
    }
    
    @objc func setPeripheralName(_ name: String) {
        self.peripheralName = name
    }
    
    @objc(addService:primary:)
    func addService(_ uuid: String, primary: Bool) {
        let serviceUUID = CBUUID(string: uuid)
        let service = CBMutableService(type: serviceUUID, primary: primary)
        if(servicesMap.keys.contains(uuid) != true){
            servicesMap[uuid] = service
        }
    }
    
    @objc(addCharacteristicToService:characteristicUUID:permissions:properties:characteristicData:)
    func addCharacteristicToService(_ serviceUUID: String, characteristicUUID: String, permissions: UInt, properties: UInt, characteristicData: String) {
        let characteristicUUID = CBUUID(string: characteristicUUID)
        let propertyValue = CBCharacteristicProperties.write
        let permissionValue = CBAttributePermissions.writeable
        let characteristic: CBMutableCharacteristic
        if(characteristicData.isEmpty) {
            characteristic = .init( type: characteristicUUID, properties: [.write, .writeWithoutResponse], value: nil, permissions: permissionValue)
        } else {
            let byteData: Data = characteristicData.data(using: .utf8)!
            characteristic = .init( type: characteristicUUID, properties: propertyValue, value: byteData, permissions: permissionValue)
        }
        
        if(servicesMap[serviceUUID] != nil) {
            if(servicesMap[serviceUUID]!.characteristics != nil) {
                servicesMap[serviceUUID]!.characteristics!.append(characteristic)
            } else {
                servicesMap[serviceUUID]!.characteristics = [characteristic]
            }
        }
    }
    
    @objc func startAdvertising(_ callback : @escaping RCTResponseSenderBlock) {
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
    
    @objc func isAdvertising() -> Bool {
        return advertising
    }
    
    @objc func stopAdvertising() {
        manager.removeAllServices()
        manager.stopAdvertising()
        advertising = false
        bleManager.printJS("Advertisement Stopped");
    }
    
    //// EVENTS
    // Respond to Read request
    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest)
    {
        let characteristic = getCharacteristic(request.characteristic.uuid)
        if (characteristic != nil){
            request.value = characteristic?.value
            manager.respond(to: request, withResult: .success)
            bleManager.printJS("characteristics \(String(describing: request.value))")
        } else {
            bleManager.printJS("cannot read, characteristic not found")
        }
    }


    // Respond to Write request
    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest])
    {
        bleManager.printJS("write request received")
        var map =  [String:Any]()
         for request in requests
         {
             let characteristic = getCharacteristic(request.characteristic.uuid)
             if (characteristic == nil) { bleManager.printJS("characteristic for writing not found") }
             if request.characteristic.uuid.isEqual(characteristic?.uuid)
             {
                 let char = characteristic as! CBMutableCharacteristic
                 char.value = request.value
                 let data = char.value ?? Data()
                 let byteArray = [UInt8](data)
                 map["device"] = request.central.identifier.uuidString
                 map["data"] = byteArray
                 bleManager.sendJSEvent("BLEManagerDidRecieveData", message: (map as! [String : NSObject]))
             } else {
                 bleManager.printJS("characteristic you are trying to access doesn't match")
             }
         }
        manager.respond(to: requests[0], withResult: .success)
    }

    // Respond to Subscription to Notification events
    public func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        let char = characteristic as! CBMutableCharacteristic
        bleManager.printJS("subscribed centrals: \(String(describing: char.subscribedCentrals))")
    }

    // Respond to Unsubscribe events
    public func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        let char = characteristic as! CBMutableCharacteristic
        bleManager.printJS("unsubscribed centrals: \(String(describing: char.subscribedCentrals))")
    }

    // Service added
    public func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error = error {
            bleManager.printJS("error: \(error)")
            return
        }
        bleManager.printJS("service: \(service)")
    }

    // Bluetooth status changed
    public func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        var state: Any
        if #available(iOS 10.0, *) {
            state = peripheral.state.description
        } else {
            state = peripheral.state
        }
        bleManager.printJS("BT state change: \(state)")
    }

    // Advertising started
    public func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            advertising = false
            bleManager.printJS("Advertising onStartFailure: \(error)")
            if(startCallback != nil) {
                startCallback!(["Advertising onStartFailure: \(error)"])
                startCallback = nil;
            }
            return
        }
        bleManager.printJS("Advert Started")
        advertising = true
        if(startCallback != nil) {
            startCallback!([NSNull()])
            startCallback = nil;
        }
        
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
