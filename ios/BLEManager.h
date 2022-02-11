//
//  BLEManager.h
//  RNBlePic
//
//  Created by MAZ on 16/11/2019.
//

#import "React/RCTBridgeModule.h"
#import "React/RCTEventEmitter.h"
#import "BLEAdvertiser.h"
#import <CoreBluetooth/CoreBluetooth.h>

@class BLEAdvertiser;

@interface BLEManager : RCTEventEmitter <RCTBridgeModule, CBCentralManagerDelegate, CBPeripheralDelegate>{
    NSString* discoverPeripherialCallbackId;
    NSMutableDictionary* connectCallbacks;
    NSMutableDictionary *readCallbacks;
    NSMutableDictionary *writeCallbacks;
    NSMutableDictionary *readRSSICallbacks;
    NSMutableDictionary *retrieveServicesCallbacks;
    NSMutableArray *writeQueue;
    NSMutableDictionary *notificationCallbacks;
    NSMutableDictionary *stopNotificationCallbacks;
    NSMutableDictionary *retrieveServicesLatches;
    BLEAdvertiser *bleAdvertiser;
}

@property (strong, nonatomic) NSMutableSet *peripherals;
@property (strong, nonatomic) CBCentralManager *manager;
@property (weak, nonatomic) NSTimer *scanTimer;

//@property (strong, nonatomic) BLEAdvertiser *bleAdvertiser;

// Returns the static CBCentralManager instance used by this library.
// May have unexpected behavior when using multiple instances of CBCentralManager.
// For integration with external libraries, advanced use only.
+(CBCentralManager *)getCentralManager;

// Returns the singleton instance of this class initiated by RN.
// For integration with external libraries, advanced use only.
+(BLEManager *)getInstance;

-(void) printJS:(NSString*)message;

-(void) sendJSEvent:(NSString*)enventName message:(NSDictionary<NSString*, NSObject*>*)message;

@end
