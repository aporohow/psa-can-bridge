# PSA CAN Bridge (APK)

Мост Robotell USB-CAN -> RealDash (TCP 35000) + ELM327-эмулятор (TCP 35001) для CronyAuto.

## Сборка APK
1. **GitHub (просто):** загрузите содержимое папки в новый репозиторий -
   workflow .github/workflows/build-apk.yml соберёт APK автоматически
   (вкладка Actions -> артефакт canbridge-apk).
2. **Android Studio:** Open -> эта папка -> Build -> Build APK(s).
3. **Командная строка:** `gradle assembleDebug`.

## Запуск
1. Установите APK (разрешить неизвестные источники).
2. Кнопка "Демо" -> RealDash: Соединения -> RealDash CAN -> TCP 127.0.0.1:35000.
3. CronyAuto: OBDII/TPMS -> WiFi -> 127.0.0.1:35001 + импорт crony_psa_pids.csv
   (лежит в assets приложения и отдельно в архиве psa_bridge_files.zip).
4. Затем "Трасса" (файл записи), затем "Адаптер" с подключённым Robotell по OTG.

## Каналы
Реализация декодера совпадает с bridge.py и psa_can_info_realdash_v29.xml:
0x21F руль, 0x0A2 магнитола, 0x1D0 климат, 0x165 зажигание,
0x0B6 RPM, 0x0F6 напряжение+одометр, 0x221 запас хода, 0x0E1 наружная температура,
кандидаты: 0x12D, 0x161, 0x2D7, 0x0E8, 0x1E8, 0x10C, 0x128, 0x261.

## Ограничения (честно)
- RobotellDevice: базовая инициализация CH340 и упрощённый разбор пакетов;
  при расхождениях с трассой сверяемся с протоколом python-can (robotell).
- PID 010D (скорость) пока не декодирован - нужна гаражная запись на 60 км/ч.
