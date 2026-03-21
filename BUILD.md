# Сборка приложения "Кресты"

## Требования

- Android Studio Arctic Fox (2020.3.1) или новее
- JDK 11 или новее
- Android SDK API 29-33
- Gradle 7.5

## Импорт проекта

1. Откройте Android Studio
2. Выберите **"Open"** и укажите папку проекта
3. Дождитесь синхронизации Gradle

## Настройка SDK

В файле `local.properties` укажите путь к Android SDK:

```properties
sdk.dir=/Users/username/Library/Android/sdk  # macOS
sdk.dir=C:\\Users\\username\\AppData\\Local\\Android\\Sdk  # Windows
sdk.dir=/home/username/Android/Sdk  # Linux
```

## Сборка debug версии

```bash
./gradlew assembleDebug
```

APK будет создан в:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Сборка release версии

### 1. Создание keystore

```bash
keytool -genkey -v -keystore kresty.keystore -alias kresty -keyalg RSA -keysize 2048 -validity 10000
```

### 2. Настройка signing

Создайте файл `app/keystore.properties`:

```properties
storeFile=../kresty.keystore
storePassword=your_store_password
keyAlias=kresty
keyPassword=your_key_password
```

### 3. Сборка

```bash
./gradlew assembleRelease
```

APK будет создан в:
```
app/build/outputs/apk/release/app-release.apk
```

## Тестирование

### На эмуляторе

1. Создайте эмулятор с API 29+
2. Запустите: `./gradlew installDebug`

### На устройстве

1. Включите **"Отладка по USB"** на устройстве
2. Подключите устройство
3. Запустите: `./gradlew installDebug`

## Важные замечания

### Work Profile на эмуляторе

Эмулятор Android может не поддерживать рабочие профили. Для тестирования рекомендуется использовать физическое устройство.

### RuStore Billing

Для тестирования покупок необходимо:
1. Загрузить приложение в RuStore Console
2. Дождаться модерации
3. Убедиться, что RuStore установлен на устройстве
4. Авторизоваться в RuStore

См. подробную инструкцию в [RUSTORE_SETUP.md](RUSTORE_SETUP.md)

### ProGuard

Release сборка использует ProGuard для обфускации кода. Правила находятся в `app/proguard-rules.pro`.

## Устранение неполадок

### Ошибка "SDK location not found"

Убедитесь, что файл `local.properties` содержит правильный путь к SDK.

### Ошибка "Gradle sync failed"

Попробуйте:
1. **File → Invalidate Caches / Restart**
2. Удалить папку `.gradle` в проекте
3. Пересинхронизировать проект

### Ошибка "Could not find com.android.tools.build:gradle"

Проверьте подключение к интернету и настройки прокси.

## CI/CD (GitHub Actions)

Пример workflow:

```yaml
name: Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'
      
      - name: Build with Gradle
        run: ./gradlew assembleDebug
      
      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```
