//
//  BLECommandContext.h
//  RNBlePic
//
//  Created by MAZ on 16/11/2019.
//

#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

@interface BLECommandContext : NSObject

@property CBPeripheral *peripheral;
@property CBService *service;
@property CBCharacteristic *characteristic;

@end
