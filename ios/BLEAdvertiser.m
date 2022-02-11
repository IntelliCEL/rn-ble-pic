//
//  BLEAdvertiser.m
//  RNBlePic
//
//  Created by MAZ on 09/02/2022.
//

#import "BLEAdvertiser.h"
#import "React/RCTBridge.h"
#import "React/RCTConvert.h"
#import "React/RCTEventDispatcher.h"
#import "NSData+Conversion.h"
#import "CBPeripheral+Extensions.h"
#import "BLECommandContext.h"

static BLEAdvertiser * _instance = nil;

@implementation BLEAdvertiser

- (instancetype)init
{
    if (self = [super init]) {
        
        peripheralName = @"";
        servicesMap = [NSMutableDictionary new];
        manager = [CBPeripheralManager new];

        advertising = false;
        advertisingRequested = false;
        bleManager = nil;

        hasListeners = false;
        startCallback = nil;
        
        _instance = self;
    }
    
    return self;
}

- (void)initialize: (BLEManager *)pBleManager
{
    bleManager = pBleManager;
    manager = [[CBPeripheralManager alloc] initWithDelegate:self queue:nil options:nil];
    advertising = false;
    peripheralName = nil;
}

- (void)setPeripheralName: (NSString *) name
{
    peripheralName = name;
    
    NSString *string = [NSString stringWithFormat:@"Peripheral Name added => %@", name];
    [bleManager printJS:string];
}

- (void) addService:(NSString *)uuid primary:(BOOL)primary
{
    CBUUID *serviceUUID = [CBUUID UUIDWithString:uuid]; // changed
    CBMutableService *service = [[CBMutableService alloc] initWithType:serviceUUID primary: primary];
    if([servicesMap objectForKey:uuid] == nil) {
        servicesMap[uuid] = service;
        
        NSString *string = [NSString stringWithFormat:@"Service added => %@", uuid];
        [bleManager printJS:string];
    }
}

- (void)addCharacteristicToService:(NSString *)sUUID characteristicUUID:(NSString *)cUUID permissions:(NSUInteger)permissions properties:(NSUInteger)properties characteristicData:(NSString *)characteristicData
{
    
    CBUUID *characteristicUUID = [CBUUID UUIDWithString:cUUID]; //changed
    CBCharacteristicProperties propertyValue = properties;
    CBAttributePermissions permissionValue = permissions;
    CBMutableCharacteristic *characteristic;
    if (!characteristicData.length) {
        characteristic = [[CBMutableCharacteristic alloc] initWithType:characteristicUUID properties:CBCharacteristicPropertyWrite+CBCharacteristicPropertyWriteWithoutResponse value:nil permissions:permissionValue];
    } else {
        NSData* byteData = [characteristicData dataUsingEncoding:NSUTF8StringEncoding];
        characteristic = [[CBMutableCharacteristic alloc] initWithType:characteristicUUID properties:propertyValue value:byteData permissions:permissionValue];
    }
    
    if(servicesMap[sUUID] != nil) {
        CBMutableService *service = servicesMap[sUUID];
        if([service characteristics] != nil) {
            NSArray* characteristics = [service characteristics];
            NSArray* updatedChars = [characteristics arrayByAddingObject:characteristic];
            [service setCharacteristics:updatedChars];
        } else {
            NSArray* characteristics = [NSArray arrayWithObject:characteristic];
            [service setCharacteristics:characteristics];
        }
        
        NSString *string = [NSString stringWithFormat:@"Char added in service => %@ => %@", sUUID, cUUID];
        [bleManager printJS:string];
    }
}

- (void)startAdvertising:(RCTResponseSenderBlock)callback
{
    advertisingRequested = true;
    if([manager state] != CBManagerStatePoweredOn) {
        NSString *string = [NSString stringWithFormat:@"Bluetooth not supported or not enabled. State => %ld", (long)[manager state]];
        [bleManager printJS:string];
        callback(@[string]);
        return;
    }
    advertisingRequested = false;
    startCallback = callback;
    
    NSDictionary<NSString *,id> * advertisementData;
    
    NSMutableArray *keys = [[NSMutableArray alloc] init];
    [keys addObject:CBAdvertisementDataServiceUUIDsKey];
    NSMutableArray<id> *objs = [[NSMutableArray alloc] init];
    [objs addObject:[self getServiceUUIDArray]];
    
    if(peripheralName != nil) {
        [keys addObject:CBAdvertisementDataLocalNameKey];
        [objs addObject:peripheralName];
    }
    advertisementData = [[NSDictionary alloc] initWithObjects:objs forKeys:keys];
    
    for (NSString* key in servicesMap) {
        [manager addService:servicesMap[key]];
    }
    [manager startAdvertising:advertisementData];
    [bleManager printJS:@"Advertisement Started."];
}

- (BOOL)isAdvertising
{
    return advertising;
}
    
- (void)stopAdvertising
{
    [manager removeAllServices];
    [manager stopAdvertising];
    advertising = false;
    [bleManager printJS:@"Advertisement Stopped."];
}

// EVENTS
// Respond to Read request
-(void)peripheralManager:(CBPeripheralManager*)peripheral didReceiveReadRequest:(CBATTRequest*)request
{
    [bleManager printJS:@"read request received"];
    CBCharacteristic * characteristic = [self getCharacteristic:[[request characteristic] UUID]];
    
    if (characteristic != nil){
        [request setValue:[characteristic value]];
        [manager respondToRequest:request withResult:CBATTErrorSuccess];
        
        NSString *string = [NSString stringWithFormat:@"characteristics %@", [request value]];
        [bleManager printJS:string];
    } else {
        [bleManager printJS:@"cannot read, characteristic not found"];
    }
}

// Respond to Write request
-(void)peripheralManager:(CBPeripheralManager*)peripheral  didReceiveWriteRequests:(NSArray<CBATTRequest *>*)requests
{
    [bleManager printJS:@"write requests received"];
    NSMutableDictionary *map = [[NSMutableDictionary alloc] init];
    
     for(CBATTRequest* request in requests)
     {
         CBCharacteristic * characteristic = [self getCharacteristic:[[request characteristic] UUID]];
         
         if (characteristic == nil) {
             [bleManager printJS:@"characteristic for writing not found"];
         }

         if([[[request characteristic] UUID] isEqual:[characteristic UUID]]) {
             CBMutableCharacteristic* charr = (CBMutableCharacteristic *)characteristic;
             charr.value = request.value;
             NSString *info =[[NSString alloc] initWithBytes:charr.value.bytes length:charr.value.length encoding:NSUTF8StringEncoding];
             
             [map setObject:request.central.identifier.UUIDString forKey:@"device"];
             [map setObject:info forKey:@"data"];
             
             [bleManager printJS:@"characteristic value updated."];
             [bleManager sendJSEvent:@"BLEManagerDidRecieveData" message:map];
         } else {
             [bleManager printJS:@"characteristic you are trying to access doesn't match"];
         }
    }
    [manager respondToRequest:requests[0] withResult:CBATTErrorSuccess];
}

// Respond to Subscription to Notification events
-(void)peripheralManager:(CBPeripheralManager*)peripheral central:(CBCentral*)central didSubscribeToCharacteristic:(CBCharacteristic*)characteristic
{
    CBMutableCharacteristic* charr = (CBMutableCharacteristic *)characteristic;
    NSString *str = [NSString stringWithFormat:@"subscribed centrals: %@",charr.subscribedCentrals];
    [bleManager printJS:str];
}

// Respond to Unsubscribe events
-(void)peripheralManager:(CBPeripheralManager*)peripheral central:(CBCentral*)central didUnsubscribeFromCharacteristic:(CBCharacteristic*)characteristic
{
    CBMutableCharacteristic* charr = (CBMutableCharacteristic *)characteristic;
    NSString *str = [NSString stringWithFormat:@"unsubscribed centrals: %@",charr.subscribedCentrals];
    [bleManager printJS:str];
}

// Service added
-(void)peripheralManager:(CBPeripheralManager*)peripheral didAddService:(CBService*)service error:(NSError *)error
{
    if(error != nil) {
        NSString *str = [NSString stringWithFormat:@"error: %@",error];
        [bleManager printJS:str];
        return;
    }
    NSString *str = [NSString stringWithFormat:@"service: %@",service];
    [bleManager printJS:str];
}

// Bluetooth status changed
- (void)peripheralManagerDidUpdateState:(nonnull CBPeripheralManager *)peripheral {
    CBManagerState state = 0;
    state = peripheral.state; // changed
    NSString *stateString = [NSString stringWithFormat:@"%@/%ld/", @"BT state change", (long)state];
    [bleManager printJS:stateString];
    if(state == CBManagerStatePoweredOn && advertisingRequested) {
        [self startAdvertising:startCallback];
    }
}

// Advertising started
-(void)peripheralManagerDidStartAdvertising:(CBPeripheralManager*)peripheral error:(NSError*)error
{
    if(error != nil) {
        advertising = false;
        NSString *str = [NSString stringWithFormat:@"Advertising onStartFailure: %@",error];
        [bleManager printJS:str];
        if(startCallback != nil) {
            startCallback(@[str]);
            startCallback = nil;
        }
        return;
    }
    [bleManager printJS:@"Advertising Started"];
    advertising = true;
    if(startCallback != nil) {
        startCallback(nil);
        startCallback = nil;
    }
}

// HELPERS
- (CBCharacteristic *)getCharacteristic:(CBUUID*)characteristicUUID
{
    for (NSString* uuid in servicesMap) {
        CBMutableService * service = servicesMap[uuid];
        if([service characteristics] == nil) {
            return nil;
        }
        for (id characteristic in [service characteristics]) {
            if([[characteristic UUID] isEqual:characteristicUUID]) {
                NSString *stateString = [NSString stringWithFormat:@"service %@ does have characteristic /%@/", uuid, characteristicUUID];
                [bleManager printJS:stateString];
                
                if ([characteristic isKindOfClass:[CBMutableCharacteristic class]]) {
                    return characteristic;
                }
                [bleManager printJS:@"but it is not mutable"];
            } else {
                [bleManager printJS:@"characteristic you are trying to access doesn't match"];
            }
        }
    }
    
    return nil;
}

- (CBCharacteristic *)getCharacteristicForService:(CBMutableService*)service characteristicUUID:(NSString*)cUUID
{
    if([service characteristics] == nil) {
        return nil;
    }
    for (id characteristic in [service characteristics]) {
        if([[characteristic UUID] isEqual:cUUID]) {
            NSString *stateString = [NSString stringWithFormat:@"service %@ does have characteristic /%@/", [service UUID], cUUID];
            [bleManager printJS:stateString];
            
            if ([characteristic isKindOfClass:[CBMutableCharacteristic class]]) {
                return characteristic;
            }
            [bleManager printJS:@"but it is not mutable"];
        } else {
            [bleManager printJS:@"characteristic you are trying to access doesn't match"];
        }
    }

    return nil;
}

-(NSMutableArray *)getServiceUUIDArray {
    NSMutableArray<CBUUID *> *serviceArray = [[NSMutableArray alloc] init];
    for (NSString* key in servicesMap) {
        [serviceArray addObject:[servicesMap[key] UUID]];
    }
    return serviceArray;
}

@end
