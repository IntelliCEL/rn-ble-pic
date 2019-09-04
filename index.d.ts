declare module "rn-ble-pic" {
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
}
