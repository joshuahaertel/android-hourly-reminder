## Doze

Some Android 6.0 phones have bug, preventing AlarmManager to be executed exact on time. You have to disabled Doze manually using adb command.

```bash
# adb shell settings put global device_idle_constants inactive_to=86400000
```

Affected phones:

  * Huawei P8lite / Huawei ALE-L21