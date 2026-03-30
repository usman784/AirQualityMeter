# Smart Air Quality Prediction & Alert System — Project Walkthrough

## What This Project Is

A native Android application that monitors, predicts, and alerts users about real-time Air Quality Index (AQI). It serves two user roles — **Citizen** and **Admin** — and uses a TensorFlow Lite ML model to predict future AQI from live and manually entered weather data.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Platform | Android (Kotlin), min SDK 26 (Android 8.0+) |
| Authentication | Firebase Authentication |
| Cloud Database | Firebase Firestore |
| Local Database | Room (offline cache) |
| ML Integration | TensorFlow Lite (`.tflite` model) |
| ML Training | Python, scikit-learn / TensorFlow (Colab) |
| Weather APIs | OpenWeatherMap, AQICN or OpenAQ |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| Background Jobs | WorkManager |
| Charts | MPAndroidChart (or similar) |
| Architecture | MVVM + Repository pattern |

---

## What Has Been Completed ✅

### Phase 1 — Authentication & Roles
- Firebase Authentication integrated for both Citizen and Admin
- Role-based routing: after login, users are directed to their respective flows
- Splash screen implemented
- Citizen profile fragment scaffolded (`CitizenProfileFragment`)
- Admin fragment scaffolded (`AdminFragment`)

---

## Feature Map (14 Use Cases)

| UC | Feature | Who | Status |
|---|---|---|---|
| UC01 | Register / Login | Both | ✅ Done |
| UC02 | View Current AQI Dashboard | Citizen | 🔲 Next |
| UC03 | Manual Data Entry + AI Prediction | Citizen | 🔲 Pending |
| UC04 | AQI Alerts / Push Notifications | Citizen | 🔲 Pending |
| UC05 | AQI History & Trend Graphs | Citizen | 🔲 Pending |
| UC06 | View Health Recommendations | Citizen | 🔲 Pending |
| UC07 | Feedback / Issue Reporting | Citizen | 🔲 Pending |
| UC08 | Manage Users | Admin | 🔲 Pending |
| UC09 | Manage AQI Datasets | Admin | 🔲 Pending |
| UC10 | Configure AQI Thresholds | Admin | 🔲 Pending |
| UC11 | Update Health Recommendations | Admin | 🔲 Pending |
| UC12 | Monitor System Activity | Admin | 🔲 Pending |
| UC13 | AI-Based AQI Prediction | System (ML) | 🔲 Pending |
| UC14 | AQI Category Classification | System (ML) | 🔲 Pending |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        Android App                          │
│                                                             │
│  ┌──────────┐   ┌──────────────┐   ┌─────────────────────┐ │
│  │ Citizen  │   │ Admin Panel  │   │   Shared Services   │ │
│  │  Flows   │   │   Flows      │   │  (Notifications,    │ │
│  └──────────┘   └──────────────┘   │   Sync, Offline)    │ │
│        │               │           └─────────────────────┘ │
│        └───────┬────────┘                    │              │
│                ▼                             ▼              │
│         ┌────────────┐             ┌──────────────────┐    │
│         │  ViewModel │             │  WorkManager     │    │
│         │  + Repo    │             │  (Background     │    │
│         └────────────┘             │   Sync/Alerts)   │    │
│           │       │                └──────────────────┘    │
│     ┌─────┘       └──────┐                                 │
│     ▼                    ▼                                  │
│  Room DB            Firestore                               │
│  (Offline)          (Cloud)                                 │
└─────────────────────────────────────────────────────────────┘
           │                   │
    Weather APIs           TFLite Model
  (OpenWeatherMap,       (AQI Prediction)
     AQICN, OpenAQ)
```

---

## AI/ML Pipeline

1. **Data Collection**: Historical AQI + weather data (Kaggle / OpenAQ datasets)
2. **Preprocessing (Python)**: Handle missing values, normalize features, feature engineering
3. **Model Training**: Regression model (Random Forest / XGBoost recommended for AQI prediction, or LSTM for time-series)
4. **Model Export**: Convert to `.tflite` using TensorFlow Lite Converter
5. **Android Integration**: Load `.tflite` via `TensorFlow Lite Interpreter`, run inference on user inputs
6. **Classification (UC14)**: Map predicted AQI value → EPA category → color code → health advice

**AQI Categories (US EPA Standard):**
| AQI Range | Category | Color |
|---|---|---|
| 0–50 | Good | 🟢 Green |
| 51–100 | Moderate | 🟡 Yellow |
| 101–150 | Unhealthy for Sensitive Groups | 🟠 Orange |
| 151–200 | Unhealthy | 🔴 Red |
| 201–300 | Very Unhealthy | 🟣 Purple |
| 301+ | Hazardous | 🟤 Maroon |

---

## Offline Strategy

- **Room DB** stores: last fetched AQI, all manual entries pending sync
- **ConnectivityManager** / `NetworkCallback` detects when internet is restored
- **WorkManager** `SyncWorker` uploads offline entries and pulls latest AQI from Firestore
- UI shows an offline banner chip when no connectivity detected

---

## Firestore Data Structure

```
/users/{uid}
  - role: "citizen" | "admin"
  - name, email, location, profileImageUrl

/aqi_records/{docId}
  - location, timestamp, aqi, temp, humidity, windSpeed, source: "api"|"manual"

/thresholds/{docId}
  - category, minAQI, maxAQI, alertMessage

/recommendations/{docId}
  - aqiCategory, title, description, iconUrl

/feedback/{docId}
  - uid, message, timestamp, status

/activity_logs/{docId}
  - uid, action, timestamp, details
```

---

## Development Phases at a Glance

| Phase | Focus | Estimated Effort |
|---|---|---|
| ✅ 1 | Auth & Setup | Done |
| 🔲 2 | Data Layer (Room + Firestore + APIs) | ~3–4 days |
| 🔲 3 | Citizen Features (UC02–UC07) | ~1–2 weeks |
| 🔲 4 | Admin Panel (UC08–UC12) | ~1 week |
| 🔲 5 | AI/ML Model Training + TFLite | ~1 week |
| 🔲 6 | Offline Sync | ~2–3 days |
| 🔲 7 | Educational/Help Screens | ~1–2 days |
| 🔲 8 | Security, Polish, Testing | ~3–4 days |
| 🔲 9 | FYP Report + Submission | ~1 week |

---

## Immediate Next Step

> **Phase 2 — Data Layer** is the critical foundation. Nothing else can be built without:
> 1. Firestore collections defined and security rules written
> 2. Room DB schema for offline caching
> 3. API service (Retrofit) wired to OpenWeatherMap / AQICN
> 4. Repository classes
>
> Once Phase 2 is complete, **UC02 (View Current AQI Dashboard)** is the natural first citizen feature to implement.
