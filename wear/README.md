# طاقتي للساعة (Wear OS Tile)

Tile بستايل Apple Watch لساعات Galaxy Watch (Wear OS 4+): حلقة نسبة البطارية، قدرة الألواح والحمل، حالة الكهرباء، ووقت آخر قراءة. يتحدث كل 15 دقيقة، والضغط على الـ Tile يحدّثه فوراً.

تطبيق مستقل `com.hussein.watchpower.wear` لا يغيّر تطبيق الهاتف. يعيد استخدام `WatchPowerClient.kt` و`SecureStore.kt` من `android/` بنفس التوقيع.

## التثبيت من الجوال (Termux + adb لاسلكي)

```sh
curl -L -o taqati-wear.apk https://github.com/Hussein199800/watch-power/releases/download/wear-latest/taqati-wear.apk
adb install -r taqati-wear.apk
adb shell "am broadcast -n com.hussein.watchpower.wear/.SetupReceiver --es usr 'اسم_المستخدم' --es pwd 'كلمة_المرور'"
```

- الإعداد يمر عبر `SetupReceiver` المحمي بصلاحية `DUMP` (يملكها adb shell فقط).
- تُحفظ بصمة SHA-1 لكلمة المرور مشفّرة بـ Android Keystore، لا كلمة المرور نفسها.
- يكتشف أول إنفرتر في الحساب تلقائياً (خادم DessMonitor ثم ShineMonitor).

## البناء

```sh
cd wear
./gradlew testDebugUnitTest assembleDebug
```

`wear/keystore/wear-debug.keystore` مفتاح تجريبي علني لتوحيد توقيع النسخ حتى تُثبَّت التحديثات فوق بعضها؛ ليس مفتاح نشر.
