## Doze

Some Android 6.0 phones have bug, preventing AlarmManager to be executed exact on time. You have to disabled Doze manually using adb command.

```bash
# adb shell settings put global device_idle_constants inactive_to=86400000
```

Affected phones:

  * Huawei P8lite / Huawei ALE-L21

Some (android 5.0) Huawai phones also prevent to triggers exact on time, change Hourly Application to: "protected" in Huawei battery manager 