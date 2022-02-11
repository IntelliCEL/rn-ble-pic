//
//  BLEAdvertiser.h
//  RNBlePic
//
//  Created by MAZ on 09/02/2022.
//

#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import "BLEManager.h"

@class BLEManager;

@interface BLEAdvertiser : NSObject <CBPeripheralManagerDelegate> {
    NSString *peripheralName;
    NSMutableDictionary *servicesMap;
    CBPeripheralManager *manager;

    BOOL advertisingRequested;
    BOOL advertising;
    BLEManager *bleManager;

    BOOL *hasListeners;
    RCTResponseSenderBlock startCallback;
}

- (void)initialize: (BLEManager *)pBleManager;
- (void)setPeripheralName: (NSString *) name;
- (void) addService:(NSString *)uuid primary:(BOOL)primary;
- (void)addCharacteristicToService:(NSString *)sUUID characteristicUUID:(NSString *)cUUID permissions:(NSUInteger)permissions properties:(NSUInteger)properties characteristicData:(NSString *)characteristicData;
- (void)startAdvertising:(RCTResponseSenderBlock)callback;
- (BOOL)isAdvertising;
- (void)stopAdvertising;
@end
