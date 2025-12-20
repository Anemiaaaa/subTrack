# 📱 subTracker

**SubTracker** — Android приложение для управления семейными подписками с поддержкой синхронизации в реальном времени.

## 📋 Описание

SubTracker позволяет семьям совместно управлять подписками на различные сервисы. Приложение поддерживает:
- ✅ Управление подписками (добавление, редактирование, удаление)
- ✅ Отслеживание платежей и истории
- ✅ Семейный доступ с синхронизацией данных
- ✅ Статистика расходов с графиками
- ✅ Напоминания о предстоящих платежах
- ✅ Вход через Google или по коду семьи
- ✅ Гостевой режим
- ✅ Экспорт данных в CSV
- ✅ Темная и светлая темы

---

## 🚀 Инструкция по запуску

### Требования

- **Android Studio** (Hedgehog или новее)
- **JDK 17** или выше
- **Android SDK** (API 23+)
- **Firebase проект** с настроенными сервисами

### Шаг 1: Клонирование репозитория

```bash
git clone <repository-url>
cd subTracker
```

### Шаг 2: Настройка Firebase

1. Создайте проект в [Firebase Console](https://console.firebase.google.com/)
2. Добавьте Android приложение с package name: `com.example.subtracker`
3. Скачайте `google-services.json` и поместите в `app/`
4. Включите следующие сервисы:
   - **Authentication** (Email/Password, Anonymous, Google)
   - **Cloud Firestore**
   - **Storage** (опционально, для аватаров)

### Шаг 3: Настройка Google Sign-In

1. В Firebase Console → Project Settings → Your apps → Android app
2. Добавьте **SHA-1 fingerprint**:
   ```bash
   # Для debug keystore:
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   
   # Для release keystore (если есть):
   keytool -list -v -keystore <path-to-keystore> -alias <alias-name>
   ```
3. Скопируйте SHA-1 и добавьте в Firebase Console

### Шаг 4: Настройка Firestore Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Семьи
    match /families/{familyCode} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // Пользователи
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Подписки
    match /subscriptions/{subId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
    
    // Платежи
    match /payments/{paymentId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
  }
}
```

### Шаг 5: Сборка и запуск

1. Откройте проект в Android Studio
2. Дождитесь синхронизации Gradle
3. Подключите устройство или запустите эмулятор (API 23+)
4. Нажмите **Run** (▶️) или `Shift+F10`

### Шаг 6: Первый запуск

1. При первом запуске создайте новую семью или войдите по коду
2. Для Google Sign-In убедитесь, что SHA-1 добавлен в Firebase

---

## 📁 Структура проекта

```
subTracker/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/subtracker/
│   │   │   │   ├── app/                    # Ядро приложения
│   │   │   │   │   ├── di/                 # Dependency Injection
│   │   │   │   │   │   └── AppGraph.kt
│   │   │   │   │   ├── MyApp.kt            # Application класс
│   │   │   │   │   └── sync/               # Синхронизация
│   │   │   │   │       ├── PendingActionsSyncWorker.kt
│   │   │   │   │       └── SyncScheduler.kt
│   │   │   │   │
│   │   │   │   ├── core/                   # Утилиты и модели
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── Periodicity.kt
│   │   │   │   │   │   └── Session.kt
│   │   │   │   │   └── time/
│   │   │   │   │       └── NextPaymentCalculator.kt
│   │   │   │   │
│   │   │   │   ├── data/                    # Data Layer
│   │   │   │   │   ├── auth/
│   │   │   │   │   │   └── FirebaseAuthRepository.kt
│   │   │   │   │   ├── local/               # Room Database
│   │   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   │   ├── dao/             # Data Access Objects
│   │   │   │   │   │   │   ├── PaymentDao.kt
│   │   │   │   │   │   │   ├── PendingActionDao.kt
│   │   │   │   │   │   │   ├── SubscriptionDao.kt
│   │   │   │   │   │   │   └── UserDao.kt
│   │   │   │   │   │   └── entity/          # Room Entities
│   │   │   │   │   │       ├── PaymentEntity.kt
│   │   │   │   │   │       ├── PendingActionEntity.kt
│   │   │   │   │   │       ├── SubscriptionEntity.kt
│   │   │   │   │   │       └── UserEntity.kt
│   │   │   │   │   ├── mapper/              # Маппинг данных
│   │   │   │   │   │   ├── PaymentDomainMappers.kt
│   │   │   │   │   │   ├── PaymentMappers.kt
│   │   │   │   │   │   ├── SubscriptionDomainMappers.kt
│   │   │   │   │   │   ├── SubscriptionMappers.kt
│   │   │   │   │   │   └── UserMappers.kt
│   │   │   │   │   ├── remote/              # Firebase
│   │   │   │   │   │   └── FirestoreSyncDataSource.kt
│   │   │   │   │   └── repository/           # Реализации репозиториев
│   │   │   │   │       ├── PaymentRepositoryImpl.kt
│   │   │   │   │       └── SubscriptionRepositoryImpl.kt
│   │   │   │   │
│   │   │   │   ├── domain/                  # Domain Layer
│   │   │   │   │   ├── auth/
│   │   │   │   │   │   ├── AuthRepository.kt
│   │   │   │   │   │   ├── AuthSession.kt
│   │   │   │   │   │   └── usecase/
│   │   │   │   │   │       ├── CreateFamilyUseCase.kt
│   │   │   │   │   │       ├── JoinFamilyUseCase.kt
│   │   │   │   │   │       ├── LoginGuestUseCase.kt
│   │   │   │   │   │       ├── LogoutUseCase.kt
│   │   │   │   │   │       └── RecoverFamilyCodeUseCase.kt
│   │   │   │   │   ├── model/                # Domain Models
│   │   │   │   │   │   ├── Payment.kt
│   │   │   │   │   │   ├── Session.kt
│   │   │   │   │   │   ├── Subscription.kt
│   │   │   │   │   │   └── User.kt
│   │   │   │   │   ├── repository/           # Repository Interfaces
│   │   │   │   │   │   ├── PaymentRepository.kt
│   │   │   │   │   │   └── SubscriptionRepository.kt
│   │   │   │   │   └── usecase/              # Use Cases
│   │   │   │   │       ├── CreateSubscriptionUseCase.kt
│   │   │   │   │       ├── DeleteSubscriptionUseCase.kt
│   │   │   │   │       ├── ObservePaymentsUseCase.kt
│   │   │   │   │       ├── ObserveSubscriptionsUseCase.kt
│   │   │   │   │       ├── PaySubscriptionUseCase.kt
│   │   │   │   │       └── UpdateSubscriptionUseCase.kt
│   │   │   │   │
│   │   │   │   ├── presentation/             # Presentation Layer
│   │   │   │   │   ├── add/
│   │   │   │   │   │   ├── AddSubscriptionViewModel.kt
│   │   │   │   │   │   └── ServiceSearchManager.kt
│   │   │   │   │   ├── auth/
│   │   │   │   │   │   ├── ForgotFamilyCodeDialogManager.kt
│   │   │   │   │   │   ├── LoginViewModel.kt
│   │   │   │   │   │   ├── LoginViewModelFactory.kt
│   │   │   │   │   │   ├── RegisterViewModel.kt
│   │   │   │   │   │   └── RegisterViewModelFactory.kt
│   │   │   │   │   ├── main/
│   │   │   │   │   │   ├── FamilyInfoDialogManager.kt
│   │   │   │   │   │   ├── FilterDialogManager.kt
│   │   │   │   │   │   ├── MainFrameUiBinder.kt
│   │   │   │   │   │   ├── MainFrameUiState.kt
│   │   │   │   │   │   ├── MainFrameViewModel.kt
│   │   │   │   │   │   ├── MainFrameViewModelFactory.kt
│   │   │   │   │   │   ├── SubscriptionAdapter.kt
│   │   │   │   │   │   ├── SubscriptionCardRenderer.kt
│   │   │   │   │   │   └── SubscriptionDialogManager.kt
│   │   │   │   │   ├── settings/
│   │   │   │   │   │   └── SettingsViewModel.kt
│   │   │   │   │   └── stats/
│   │   │   │   │       ├── PaymentCardRenderer.kt
│   │   │   │   │       ├── StatsUiState.kt
│   │   │   │   │       └── StatsViewModel.kt
│   │   │   │   │
│   │   │   │   ├── ui/                       # UI Theme (Compose)
│   │   │   │   │   └── theme/
│   │   │   │   │       ├── Color.kt
│   │   │   │   │       ├── Theme.kt
│   │   │   │   │       └── Type.kt
│   │   │   │   │
│   │   │   │   ├── MainActivity.kt           # Экран входа
│   │   │   │   ├── RegisterActivity.kt       # Регистрация семьи
│   │   │   │   ├── MainFrameActivity.kt      # Главный экран
│   │   │   │   ├── AddSubscriptionActivity.kt # Добавление подписки
│   │   │   │   ├── StatsActivity.kt          # Статистика
│   │   │   │   ├── SettingsActivity.kt      # Настройки
│   │   │   │   │
│   │   │   │   ├── SessionManager.kt         # Управление сессией
│   │   │   │   ├── GuestSession.kt           # Гостевой режим
│   │   │   │   ├── ThemeManager.kt           # Управление темой
│   │   │   │   ├── SubscriptionReminderManager.kt # Напоминания
│   │   │   │   ├── ServiceItems.kt           # Список сервисов
│   │   │   │   └── FirebaseSubscription.kt    # Firebase модель
│   │   │   │
│   │   │   ├── res/                          # Ресурсы
│   │   │   │   ├── layout/                   # XML layouts
│   │   │   │   ├── layout-land/              # Landscape layouts
│   │   │   │   ├── values/                   # Strings, colors, themes
│   │   │   │   ├── drawable/                  # Icons, images
│   │   │   │   └── xml/                       # FileProvider configs
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   ├── androidTest/                      # Android тесты
│   │   └── test/                              # Unit тесты
│   │
│   ├── build.gradle.kts                      # Конфигурация модуля
│   ├── google-services.json                   # Firebase конфигурация
│   └── proguard-rules.pro                    # ProGuard правила
│
├── build.gradle.kts                           # Корневой build файл
├── settings.gradle.kts                        # Настройки проекта
├── gradle/
│   └── libs.versions.toml                     # Версии зависимостей
└── README.md                                  # Этот файл
```

---

## 🏗️ Архитектура

### Clean Architecture + MVVM

Проект использует **Clean Architecture** с разделением на три слоя и паттерн **MVVM** для Presentation слоя.

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Activities  │  │  ViewModels  │  │    UI State   │      │
│  │              │  │              │  │              │      │
│  │ MainActivity │  │ LoginViewModel│  │ MainFrameUi  │      │
│  │ MainFrame... │  │ MainFrameVM  │  │ State        │      │
│  │ Settings...  │  │ StatsViewModel│  │ StatsUiState │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                 │                  │              │
│         └─────────────────┼──────────────────┘              │
│                           │                                 │
└───────────────────────────┼─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Use Cases   │  │   Models     │  │ Repositories │      │
│  │              │  │              │  │  (Interfaces) │      │
│  │ CreateSub    │  │ Subscription │  │ Subscription │      │
│  │ UpdateSub    │  │ Payment     │  │ Repository   │      │
│  │ PaySub       │  │ User        │  │ Payment      │      │
│  │ JoinFamily   │  │ Session     │  │ Repository   │      │
│  └──────┬───────┘  └──────────────┘  └──────┬───────┘      │
│         │                                    │              │
└─────────┼────────────────────────────────────┼──────────────┘
          │                                    │
          │                                    ▼
┌─────────┼────────────────────────────────────┼──────────────┐
│         │                                    │              │
│         ▼                                    ▼              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                  DATA LAYER                          │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │  │
│  │  │ Repositories│  │   Local     │  │  Remote    │ │  │
│  │  │  (Impl)     │  │   (Room)    │  │ (Firestore)│ │  │
│  │  │             │  │              │  │            │ │  │
│  │  │ Subscription│  │ AppDatabase │  │ Firestore  │ │  │
│  │  │ Repository  │  │ Subscription│  │ Sync       │ │  │
│  │  │ Payment     │  │ Entity      │  │ DataSource │ │  │
│  │  │ Repository  │  │ Payment     │  │            │ │  │
│  │  │             │  │ Entity      │  │            │ │  │
│  │  │             │  │ UserEntity  │  │            │ │  │
│  │  └──────────────┘  └──────────────┘  └────────────┘ │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### Поток данных

```
User Action (UI)
    │
    ▼
ViewModel
    │
    ▼
Use Case
    │
    ▼
Repository (Interface)
    │
    ├──► RepositoryImpl
    │       │
    │       ├──► Room (Local DB) ──► Offline-first
    │       │
    │       └──► Firestore (Remote) ──► Sync via PendingActions
    │
    ▼
Domain Model
    │
    ▼
UI State Update
    │
    ▼
UI Re-render
```

### Синхронизация данных

```
┌─────────────────────────────────────────────────────────┐
│              OFFLINE-FIRST STRATEGY                      │
└─────────────────────────────────────────────────────────┘

User Action (Create/Update/Delete)
    │
    ▼
┌─────────────────┐
│  Room Database  │  ← Сохраняем локально сразу
│  (Immediate)    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ PendingActions  │  ← Добавляем в очередь синхронизации
│  (Queue)        │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  WorkManager    │  ← Фоновая синхронизация
│  Sync Worker    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Firestore     │  ← Отправка на сервер
│   (Remote)      │
└─────────────────┘
```

---

## 🗄️ Схема базы данных

### Room Database (Локальная БД)

#### Таблица: `subscriptions`

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | String (PK) | Firestore document ID |
| `familyCode` | String | Код семьи |
| `ownerUid` | String | UID владельца |
| `ownerUsername` | String | Имя владельца |
| `name` | String | Название подписки |
| `price` | Double | Цена |
| `periodicity` | String | Периодичность (monthly, yearly, etc.) |
| `iconResName` | String | Имя ресурса иконки |
| `nextPaymentDate` | Long | Дата следующего платежа (timestamp) |
| `updatedAt` | Long | Время последнего обновления |

**Индексы:** `familyCode`, `ownerUid`, `nextPaymentDate`

#### Таблица: `payments`

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | String (PK) | Firestore document ID |
| `familyCode` | String | Код семьи |
| `subscriptionName` | String | Название подписки |
| `amount` | Double | Сумма платежа |
| `ownerUid` | String | UID владельца |
| `ownerUsername` | String | Имя владельца |
| `iconResName` | String | Имя ресурса иконки |
| `paidAt` | Long | Дата оплаты (timestamp) |
| `updatedAt` | Long | Время последнего обновления |

**Индексы:** `familyCode`, `paidAt`, `ownerUid`

#### Таблица: `users`

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | String (PK) | Firestore document ID |
| `uid` | String | Firebase Auth UID |
| `username` | String | Имя пользователя |
| `familyCode` | String | Код семьи |
| `familyName` | String | Название семьи |
| `role` | String | Роль (admin, member) |
| `avatarUrl` | String | Путь к локальному файлу аватара |
| `updatedAt` | Long | Время последнего обновления |

**Индексы:** `familyCode`, `uid`

#### Таблица: `pending_actions`

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | String (PK) | UUID действия |
| `familyCode` | String | Код семьи |
| `type` | String | Тип действия (PAY, UPDATE, DELETE) |
| `subId` | String | ID подписки |
| `payloadJson` | String | JSON с данными действия |
| `createdAt` | Long | Время создания |

**Индексы:** `familyCode`, `createdAt`

### Firestore (Удаленная БД)

#### Коллекция: `families`

```javascript
families/{familyCode}
{
  familyName: string,
  createdAt: timestamp,
  adminUid: string
}
```

#### Коллекция: `users`

```javascript
users/{userId}
{
  uid: string,
  username: string,
  familyCode: string,
  familyName: string,
  role: "admin" | "member",
  avatarUrl: string,
  provider: "google" | "manual"
}
```

#### Коллекция: `subscriptions`

```javascript
subscriptions/{subId}
{
  familyCode: string,
  ownerUid: string,
  ownerUsername: string,
  name: string,
  price: number,
  periodicity: string,
  iconResName: string,
  nextPaymentDate: timestamp,
  createdAt: timestamp,
  updatedAt: timestamp
}
```

#### Коллекция: `payments`

```javascript
payments/{paymentId}
{
  familyCode: string,
  subscriptionName: string,
  amount: number,
  ownerUid: string,
  ownerUsername: string,
  iconResName: string,
  paidAt: timestamp,
  createdAt: timestamp
}
```

### Диаграмма связей

```
┌─────────────┐
│  families   │
│             │
│ familyCode  │◄─────┐
│ familyName  │      │
└─────────────┘      │
                     │
                     │ (1:N)
┌─────────────┐      │
│   users     │      │
│             │      │
│ uid         │──────┘
│ familyCode  │
│ username    │
│ role        │
│ avatarUrl   │
└─────────────┘
      │
      │ (1:N)
      │
      ▼
┌─────────────┐
│subscriptions│
│             │
│ id          │
│ familyCode  │
│ ownerUid    │
│ name        │
│ price       │
│ periodicity │
│ nextPayment │
└──────┬──────┘
       │
       │ (1:N)
       │
       ▼
┌─────────────┐
│  payments   │
│             │
│ id          │
│ familyCode  │
│ subName     │
│ amount      │
│ paidAt      │
└─────────────┘
```

---

## 🛠️ Технологии

### Основные библиотеки

- **Android Jetpack**
  - Lifecycle (2.8.4)
  - Room (2.6.1)
  - WorkManager (2.8.1)
  - ViewModel & LiveData

- **Firebase**
  - Authentication
  - Cloud Firestore
  - Storage (опционально)

- **Google Services**
  - Play Services Auth (20.7.0) - Google Sign-In

- **UI**
  - Material Components
  - Glide (4.16.0) - загрузка изображений
  - MPAndroidChart (v3.1.0) - графики

- **Kotlin**
  - Coroutines (1.7.3)
  - Flow

