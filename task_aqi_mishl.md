# Smart Air Quality Prediction & Alert System — FYP Task Checklist

## ✅ Phase 1: Project Setup & Authentication (DONE)
- [x] Project structure created (Android, Kotlin, Firebase)
- [x] Firebase Authentication integrated
- [x] Citizen Registration & Login (UC01)
- [x] Admin Registration & Login (UC01)
- [x] Splash screen implemented
- [x] Role-based navigation (Citizen → Citizen flow, Admin → Admin flow)

---

## 🔲 Phase 2: Core Architecture & Data Layer
- [ ] Define data models: `AQIRecord`, `UserProfile`, `HealthRecommendation`, `AlertThreshold`, `FeedbackModel`
- [ ] Setup Firebase Firestore collections: `users`, `aqi_records`, `thresholds`, `recommendations`, `feedback`, `activity_logs`
- [ ] Setup Room database for offline caching (last AQI, manual entries)
- [ ] Create Repository layer (AQIRepository, UserRepository)
- [ ] Implement offline-first sync strategy (WorkManager or ConnectivityManager)
- [ ] Integrate Retrofit for API calls (OpenWeatherMap, AQICN / OpenAQ)
- [ ] Create API service interfaces and DTOs

---

## 🔲 Phase 3: Citizen Features

### UC02 – View Current AQI (Dashboard)
- [ ] Citizen home/dashboard fragment
- [ ] Fetch live AQI via API (location-based)
- [ ] Display AQI value with color-coded indicator (Good/Moderate/Unhealthy/Hazardous)
- [ ] Show weather parameters (temp, humidity, wind speed)
- [ ] Offline fallback: show last synced AQI from Room DB

### UC03 – Manual Data Entry & AI Prediction
- [ ] Manual entry form (temp, humidity, wind speed, PM2.5 optional)
- [ ] Input validation
- [ ] Preprocess inputs and run TensorFlow Lite model
- [ ] Display predicted AQI + category + recommendations
- [ ] Store manual entry locally; sync to Firestore on reconnect

### UC04 – AQI Alerts & Push Notifications
- [ ] Firebase Cloud Messaging (FCM) integration
- [ ] Background AQI check against configured thresholds (WorkManager)
- [ ] Trigger push notification when AQI > threshold
- [ ] In-app fallback alert if notifications are blocked

### UC05 – AQI History & Trend Analysis
- [ ] History screen with date-range picker (daily / weekly / monthly)
- [ ] Render AQI trend graphs (MPAndroidChart or similar)
- [ ] Fetch history from Firestore; cache in Room DB
- [ ] Handle empty state gracefully

### UC06 – Health Recommendations
- [ ] Health recommendations screen
- [ ] Load recommendations from Firestore (admin-managed)
- [ ] Map recommendations to AQI category
- [ ] Display with icons and readable formatting

### UC07 – Feedback & Issue Reporting
- [ ] Feedback form (text input + optional category)
- [ ] Submit to Firestore `feedback` collection
- [ ] Success/failure feedback to user

### Profile Management
- [ ] Citizen profile view & update (name, profile image, location)
- [ ] Password reset via Firebase Auth

---

## 🔲 Phase 4: Admin Panel

### UC08 – User Management
- [ ] Admin user list screen (paginated)
- [ ] View user details
- [ ] Deactivate / delete user
- [ ] Role update if needed

### UC09 – Manage AQI Datasets
- [ ] Dataset list screen
- [ ] Add / edit / delete AQI records
- [ ] Input validation for dataset fields
- [ ] Sync changes to Firestore

### UC10 – Configure AQI Thresholds & Alerts
- [ ] Threshold configuration screen
- [ ] Set Good / Moderate / Unhealthy / Very Unhealthy / Hazardous breakpoints
- [ ] Save to Firestore; propagate to FCM trigger logic

### UC11 – Update Health Recommendations
- [ ] Recommendations management screen (CRUD)
- [ ] Map each recommendation to an AQI category
- [ ] Save to Firestore

### UC12 – Monitor System Activity & Alerts
- [ ] Activity log screen (user logins, manual submissions, alerts triggered)
- [ ] Real-time listener on Firestore activity logs
- [ ] Summary stats (total users, alerts sent today, etc.)

---

## 🔲 Phase 5: AI / ML Integration

### UC13 – AQI Prediction Model
- [ ] Collect & preprocess historical AQI + weather dataset (Python/Colab)
- [ ] Train regression model (e.g., Random Forest, XGBoost, or LSTM)
- [ ] Evaluate model performance (MAE, RMSE, R²)
- [ ] Convert model to TensorFlow Lite (`.tflite`)
- [ ] Integrate `.tflite` model into Android via `TensorFlow Lite` interpreter
- [ ] Map model inputs: [temp, humidity, wind_speed, pm2.5(?)] → AQI output

### UC14 – AQI Classification
- [ ] Define AQI category breakpoints (US EPA standard)
- [ ] Classification logic: map raw AQI → category + color + advice
- [ ] Reusable `AQIClassifier` utility class

---

## 🔲 Phase 6: Offline Functionality
- [ ] Room DB schema for offline AQI cache and manual entries
- [ ] ConnectivityManager / NetworkCallback for sync triggers
- [ ] WorkManager sync job: upload offline entries when online
- [ ] Show offline banner/chip in UI when no internet available

---

## 🔲 Phase 7: Educational & Support Features
- [ ] Health guides & FAQ screen (static or Firestore-backed)
- [ ] Tutorials / onboarding tips for new citizens
- [ ] About / app info screen

---

## 🔲 Phase 8: Non-Functional & Quality
- [ ] Secure API key storage (local.properties / BuildConfig, not committed)
- [ ] Encrypt sensitive Firestore fields if required
- [ ] Firestore Security Rules (citizens access only own data; admin full access)
- [ ] Optimize TFLite model size for low-latency inference
- [ ] Background fetch battery optimization (WorkManager constraints)
- [ ] Responsive layout for various Android screen sizes (min SDK 26 / Android 8.0)
- [ ] ProGuard / R8 rules for release build

---

## 🔲 Phase 9: Testing & QA
- [ ] Unit tests: AQI classifier, preprocessor, repository logic
- [ ] Integration tests: API fetch, Firestore CRUD
- [ ] UI tests: login, dashboard, manual entry, admin CRUD flows
- [ ] Edge case: offline mode, invalid inputs, API timeout

---

## 🔲 Phase 10: Final Polish & Submission
- [ ] App icon, splash branding
- [ ] README / documentation
- [ ] FYP report sections: system design, ER diagram, sequence diagrams, screenshots
- [ ] APK build and device testing
- [ ] Presentation preparation
