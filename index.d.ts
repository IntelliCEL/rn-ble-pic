declare module "rn-ble-pic" {
  export function init(): Promise<void>;

  // Android only
  export function enableBluetooth(): Promise<void>;
  // [Android only]

  export function checkState(): void;

  export function setPeripheralName(name?: string): void;

  export function addService(
    serviceUUID?: string,
    primary?: boolean,
    serviceData?: string
  ): void;

  export function addCharacteristicToService(
    serviceUUID?: string,
    charUUID?: string,
    permissions?: int,
    properties?: int,
    data?: string
  ): void;

  export function startAdvertising(): Promise<void>;

  export function isAdvertising(): boolean;

  export function stopAdvertising(): void;

  export interface Peripheral {
    id: string;
    rssi: number;
    name?: string;
    advertising: AdvertisingData;
  }

  export interface AdvertisingData {
    isConnectable?: boolean;
    localName?: string;
    manufacturerData?: any;
    serviceUUIDs?: string[];
    serviceData?: ServiceData[];
    txPowerLevel?: number;
  }

  export interface ServiceData {
    bytes?: boolean[];
    data?: string;
  }

  export interface ScanOptions {
    numberOfMatches?: number;
    matchMode?: number;
    scanMode?: number;
  }

  export function scan(
    serviceUUIDs: string[],
    seconds: number,
    options?: ScanOptions
  ): Promise<void>;

  export function stopScan(): Promise<void>;

  export function connect(peripheralID: string): Promise<void>;

  export function disconnect(
    peripheralID: string,
    force?: boolean
  ): Promise<void>;

  export interface PeripheralInfo {}
  export function retrieveServices(
    peripheralID: string,
    serviceUUIDs?: string[]
  ): Promise<PeripheralInfo>;

  export function startNotification(
    peripheralID: string,
    serviceUUID: string,
    characteristicUUID: string
  ): Promise<void>;

  export function stopNotification(
    peripheralID: string,
    serviceUUID: string,
    characteristicUUID: string
  ): Promise<void>;

  export function write(
    peripheralID: string,
    serviceUUID: string,
    characteristicUUID: string,
    data: any,
    maxByteSize?: number
  ): Promise<void>;

  export function writeWithoutResponse(
    peripheralID: string,
    serviceUUID: string,
    characteristicUUID: string,
    data: any,
    maxByteSize?: number,
    queueSleepTime?: number
  ): Promise<void>;

  export function read(
    peripheralID: string,
    serviceUUID: string,
    characteristicUUID: string
  ): Promise<any>;

  export function getDiscoveredPeripherals(): Promise<any[]>;

  export function getConnectedPeripherals(
    serviceUUIDs: string[]
  ): Promise<any[]>;

  export function removePeripheral(peripheralID: string): Promise<void>;

  export function isPeripheralConnected(
    peripheralID: string,
    serviceUUIDs: string[]
  ): Promise<boolean>;
}
