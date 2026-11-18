# Qobuz Bridge

Модуль Xposed для [Vector](https://github.com/JingMatrix/Vector) / LSPosed. Позволяет
[vPro TV Launcher](https://github.com/muzdai/com.muzdai.launcher) включать трек или альбом в Qobuz для Android TV
по номеру из каталога, без нажатий по экрану Qobuz.

## Как работает

- Модуль работает только внутри Qobuz (область `com.qobuz.music`), систему не трогает.
- При запуске Qobuz он находит его внутренний контроллер очереди (`MediaQueueController`) и метод «заменить очередь
  и играть» по строке, которую этот метод пишет в журнал. Поиск делает [DexKit](https://github.com/LuckyPray/DexKit),
  поэтому обфусцированные имена Qobuz не прописаны в коде.
- Лаунчер находит треки в каталоге Qobuz и шлёт модулю рассылку `com.muzdai.qobuzbridge.PLAY` (extras `tracks` —
  JSON, `start`, `query`). Модуль собирает из них элементы очереди Qobuz (`MediaQueueItem`) и вызывает этот метод.
- Команды принимаются только от `com.muzdai.launcher`: на Android 14 модуль проверяет отправителя рассылки.
- Ответ: `1` — играет, `0` — плеер Qobuz ещё не создан, `-1` — отказ или ошибка (текст в данных ответа).
  `com.muzdai.qobuzbridge.PING` — проверка, что модуль на месте.

## Установка

1. Vector (или LSPosed) в Magisk с включённым Zygisk.
2. Установить APK, включить модуль и область:
   ```
   cli=/data/adb/modules/zygisk_vector/cli
   su -c "$cli modules enable com.muzdai.qobuzbridge"
   su -c "$cli scope set com.muzdai.qobuzbridge com.qobuz.music/0"
   ```
3. Перезапустить Qobuz. В журнале Vector появится `QobuzBridge: … ready: <класс>.<метод>`.

Проверено на Qobuz для Android TV 2.0.1.1-beta, TCL 65C8K (Google TV 14, 32-битная система).

## Сборка

`./gradlew assembleRelease`. Подпись берётся из переменных `SIGNING_*` (в GitHub Actions — из секретов), без них
подписывает отладочный ключ. Релиз собирается на GitHub по тегу `v*`.
