# rn-ble-pic

## Getting started

`$ npm install rn-ble-pic --save`

### Mostly automatic installation

`$ react-native link rn-ble-pic`

### Manual installation

#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `rn-ble-pic` and add `RNBlePic.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libRNBlePic.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)

#### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`

- Add `import com.blepic.RNBlePicPackage;` to the imports at the top of the file
- Add `new RNBlePicPackage()` to the list returned by the `getPackages()` method

2. Append the following lines to `android/settings.gradle`:
   ```
   include ':rn-ble-pic'
   project(':rn-ble-pic').projectDir = new File(rootProject.projectDir, 	'../node_modules/rn-ble-pic/android')
   ```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
   ```
     compile project(':rn-ble-pic')
   ```

## Usage

```javascript
// get an instance of BLEPIC module
import BLEPIC from "rn-ble-pic";

// add peripheral name
BLEPIC.setPeripheralName(name: string);

// add services
BLEPIC.addService(serviceUUID: string, primary: boolean, serviceData: string);

// add characteristics to a service
BLEPIC.addCharacteristicToService(serviceUUID: string, charUUID: string, permissions: int, properties: int, data: string);

// start advertising
BLEPIC.startAdvertising();

// check if currently advertising or not
BLEPIC.isAdvertising();

// stop advertising
BLEPIC.stopAdvertising();
```
