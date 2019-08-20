# rn-ble-pic

## Getting started

`$ npm install react-native-ble-pic --save`

### Mostly automatic installation

`$ react-native link react-native-ble-pic`

### Manual installation

#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-ble-pic` and add `RNBlePic.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libRNBlePic.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`

- Add `import com.reactlibrary.RNBlePicPackage;` to the imports at the top of the file
- Add `new RNBlePicPackage()` to the list returned by the `getPackages()` method

2. Append the following lines to `android/settings.gradle`:
   ```
   include ':react-native-ble-pic'
   project(':react-native-ble-pic').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-ble-pic/android')
   ```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
   ```
     compile project(':react-native-ble-pic')
   ```

## Usage

```javascript
import RNBlePic from "react-native-ble-pic";

// TODO: What to do with the module?
RNBlePic;
```
