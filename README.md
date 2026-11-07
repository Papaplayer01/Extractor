# Expense Tracker (Android + Django REST)

An offline-first expense tracker. The Android app is built with Kotlin and Jetpack Compose (MVVM with ViewModel and StateFlow), Retrofit for REST/JSON, and Room as a local cache. The backend is Django REST Framework with JWT login.

## How offline support works
All reads come from Room. Adds, edits and deletes are written to Room first and flagged as pending, then pushed to the server. If there is no connection, they stay pending and sync on the next successful refresh.

## Run the backend
    cd backend
    python -m venv venv && source venv/bin/activate   # Windows: venv\Scripts\activate
    pip install -r requirements.txt
    python manage.py makemigrations expenses
    python manage.py migrate
    python manage.py runserver 0.0.0.0:8000

## Run the Android app
1. In Android Studio create a new **Empty Activity (Compose)** project named ExpenseTracker with package `com.example.expensetracker`.
2. Copy `android/app/build.gradle.kts`, the root `build.gradle.kts`, `AndroidManifest.xml` and the three files in `app/src/main/java/com/example/expensetracker/` over the generated ones, then sync Gradle.
3. Start the backend, run the app on an emulator (it reaches the server at `10.0.2.2:8000`). On a real phone, change `BASE_URL` in `Data.kt` to your computer's LAN IP.

## API
| Method | Endpoint | Purpose |
|---|---|---|
| POST | /api/auth/register/ | Create account |
| POST | /api/auth/login/ | Get JWT access token |
| GET/POST | /api/expenses/ | List / create |
| PUT/DELETE | /api/expenses/{id}/ | Update / delete |

## Screenshots
Add login, list and edit screenshots here.

## Optional: Firebase
Create a Firebase project, add the Android app, place `google-services.json` in `app/`, and add the Firebase BoM plus Firebase Auth or Cloud Messaging dependencies.
