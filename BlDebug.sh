/u/67/elharim1/unix/Android/Sdk/platform-tools/adb forward tcp:4444 localabstract:/adb-hub

/u/67/elharim1/unix/Android/Sdk/platform-tools/adb connect 127.0.0.1:4444 

/u/67/elharim1/unix/Android/Sdk/platform-tools/adb devices

/u/67/elharim1/unix/Android/Sdk/platform-tools/adb -s LHS7N18A26004026 pull /sdcard/shared/Drawing_Data

/u/67/elharim1/unix/Android/Sdk/platform-tools/adb -s 127.0.0.1:4444 pull /sdcard/shared/Accelerometer_Data
