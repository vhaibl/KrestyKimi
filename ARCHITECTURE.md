# Архитектура приложения "Кресты"

## Общая схема

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer                                 │
├─────────────────────────────────────────────────────────────────┤
│  MainActivity    SetupActivity   AppListActivity   Subscription  │
│       │               │               │               Activity   │
│       └───────────────┴───────────────┴─────────────────────────┘
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    Adapters                              │    │
│  │  AppsAdapter          AppSelectionAdapter               │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Business Logic Layer                        │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │ WorkProfile │  │ Preferences │  │    BillingManager       │ │
│  │  Manager    │  │   Manager   │  │                         │ │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Android System Layer                          │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌──────────────────────────────────────┐ │
│  │ DevicePolicyManager│  │         RuStore Billing SDK        │ │
│  │   (Work Profile)   │  │            (v10.0.0)               │ │
│  └─────────────────┘  └──────────────────────────────────────┘ │
│                              │                                   │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              Android Work Profile (OS Level)               │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Компоненты

### Activities

| Activity | Назначение |
|----------|-----------|
| `MainActivity` | Главный экран, список изолированных приложений |
| `SetupActivity` | Настройка рабочего профиля |
| `AppListActivity` | Выбор приложений для клонирования |
| `SubscriptionActivity` | Управление подписками |

### Managers

| Manager | Назначение |
|---------|-----------|
| `WorkProfileManager` | Управление рабочим профилем: создание, клонирование, заморозка |
| `PreferencesManager` | Хранение настроек и состояния |
| `RuStoreBillingManager` | Интеграция с RuStore Billing SDK |

### Receivers

| Receiver | Назначение |
|----------|-----------|
| `KrestyDeviceAdminReceiver` | Получение прав администратора устройства |
| `BootReceiver` | Восстановление состояния после перезагрузки |

## Поток данных

### Создание рабочего профиля

```
Пользователь
    │
    ▼
SetupActivity.startWorkProfileProvisioning()
    │
    ▼
WorkProfileManager.startWorkProfileProvisioning()
    │
    ▼
Intent(ACTION_PROVISION_MANAGED_PROFILE)
    │
    ▼
Android System (создание профиля)
    │
    ▼
KrestyDeviceAdminReceiver.onProfileProvisioningComplete()
    │
    ▼
Broadcast → MainActivity (обновление UI)
```

### Клонирование приложения

```
Пользователь выбирает приложение
    │
    ▼
AppListActivity.addAppToWorkProfile()
    │
    ▼
WorkProfileManager.cloneAppToWorkProfile()
    │
    ▼
DevicePolicyManager.enableSystemApp()
    │
    ▼
Приложение доступно в рабочем профиле
```

### Покупка подписки

```
Пользователь нажимает "Подписаться"
    │
    ▼
SubscriptionActivity.launchBillingFlow()
    │
    ▼
RuStoreBillingManager.launchBillingFlow()
    │
    ▼
RuStore Pay UI
    │
    ▼
Deeplink обратно в приложение
    │
    ▼
RuStoreBillingManager.queryPurchases()
    │
    ▼
PreferencesManager.setSubscriptionTier()
    │
    ▼
UI обновляется
```

## Модели данных

### AppInfo

```kotlin
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val isInstalledInWorkProfile: Boolean,
    val isFrozen: Boolean
)
```

### SubscriptionTier

```kotlin
sealed class SubscriptionTier {
    object Free : maxApps = 1
    object Basic : maxApps = 3
    object Unlimited : maxApps = Int.MAX_VALUE
}
```

## Хранение данных

### SharedPreferences

| Ключ | Тип | Описание |
|------|-----|----------|
| `subscription_tier` | String | Текущий уровень подписки |
| `frozen_apps` | Set<String> | Список замороженных приложений |
| `work_profile_created` | Boolean | Создан ли рабочий профиль |

## Разрешения

```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />
<uses-permission android:name="com.android.vending.BILLING" />
```

## Безопасность

1. **Device Admin** — приложение получает права администратора только для управления рабочим профилем
2. **Изоляция** — приложения в рабочем профиле не имеют доступа к данным основного профиля
3. **Шифрование** — данные рабочего профиля шифруются системой Android

## Ограничения

1. **Work Profile** — не все устройства поддерживают рабочие профили
2. **API 29+** — минимальная версия Android 10
3. **Google Play** — монетизация требует Google Play Services
