
#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(BLEManager, RCTEventEmitter)

RCT_EXTERN_METHOD(
    init:
    (nonnull RCTResponseSenderBlock)callback
)
RCT_EXTERN_METHOD(
    isAdvertising:
    (RCTPromiseResolveBlock)resolve
    rejecter: (RCTPromiseRejectBlock)reject
)
RCT_EXTERN_METHOD(
    setPeripheralName: (NSString *)string
)
RCT_EXTERN_METHOD(
    addService: (NSString *)uuid
    primary:    (BOOL)primary
)
RCT_EXTERN_METHOD(
    addCharacteristicToService: (NSString *)serviceUUID
    uuid:                       (NSString *)uuid
    permissions:                (NSInteger *)permissions
    properties:                 (NSInteger *)properties
    data:                       (NSString *)data
)
RCT_EXTERN_METHOD(
    startAdvertising:
    (nonnull RCTResponseSenderBlock)callback
)
RCT_EXTERN_METHOD(stopAdvertising)
RCT_EXTERN_METHOD(
    sendNotificationToDevices: (NSString *)characteristicUUID
    data: (NSString *)data
)
RCT_EXTERN_METHOD(requiresMainQueueSetup)

@end
