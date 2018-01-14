## Doze

Some Android 6.0 phones have a bug, that prevents AlarmManager to be executed exact on time. You have to disable Doze manually using adb command.

```bash
# adb shell settings put global device_idle_constants inactive_to=86400000
```

Affected phones:

  * Huawei P8lite / Huawei ALE-L21

Some (android 5.0) Huawai phones also prevent triggers to be exact on time, in order to fix - change Hourly Application to: "protected" in Huawei battery manager 

  * https://sensorberg-dev.github.io/2016/12/Huawei_delayed_notification_issue/
  * http://stackoverflow.com/questions/31638986/protected-apps-setting-on-huawei-phones-and-how-to-handle-it/35220476
