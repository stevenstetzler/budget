# Budget

A personal budgeting application with a Python/Flask backend, a web frontend, a FastAPI backend (alternative), and a native Android app.

## Repository Structure

```
budget/
├── server/          # Flask backend (primary Python server)
├── web/             # Web frontend (single-page app)
├── python/          # FastAPI backend (alternative Python server)
└── app/             # Android app (Kotlin / Jetpack Compose)
```

## Quick Start (Flask + Web)

```bash
cd server
pip install -r requirements.txt
python app.py
```

Then open **http://localhost:5000/budget/** in your browser.

---

## Server (Flask)

The primary backend is a lightweight [Flask](https://flask.palletsprojects.com/) server located in `server/`.

### Requirements

- Python 3.10+

### Installation

```bash
cd server
pip install -r requirements.txt
```

### Running

```bash
python app.py
```

- Server starts on **http://localhost:5000**
- API routes are prefixed with `/budget/api/`
- Web frontend is served at **http://localhost:5000/budget/**

To enable Flask debug/reload mode (development only):

```bash
FLASK_DEBUG=1 python app.py
```

### Data Storage

Data is persisted in `budget.db` (SQLite) in the directory where `app.py` is run. The database is created automatically on first start and seeded with default categories.

See [`server/README.md`](server/README.md) for the full API reference.

---

## Web Frontend

The web frontend is a single-page app located at `web/index.html`. It can be used in two ways:

1. **Served by Flask** – visit **http://localhost:5000/budget/** after starting the Flask server.
2. **Opened directly** – open `web/index.html` in any browser, then go to **Preferences** (⚙️) and set the **Endpoint URL** to `http://localhost:5000` (or wherever the server is running).

> CORS is enabled on the Flask server for all origins, so opening `index.html` directly from the filesystem works without any additional configuration.

---

## Python Backend (FastAPI – Alternative)

An alternative backend using [FastAPI](https://fastapi.tiangolo.com/) is located in `python/`.

### Requirements

- Python 3.10+

### Installation

```bash
cd python
pip install -r requirements.txt
```

### Running

```bash
uvicorn main:app --reload
```

- Server starts on **http://localhost:8000**
- Interactive API docs: **http://localhost:8000/docs**

---

## Android App

The native Android app is located in `app/` and is built with Kotlin and Jetpack Compose.

### Requirements

- Android Studio (latest stable)
- Android SDK with minSdk 24 / targetSdk 36
- Java 11

### Building

```bash
./gradlew assembleDebug
```

The debug APK will be output to `app/build/outputs/apk/debug/`.

### Running

Open the project in Android Studio and run it on a device or emulator, or install the APK directly:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Releasing

Signed release builds (AAB + APK) are published automatically to GitHub Releases and Google Play Internal Testing when a version tag is pushed.  See [`docs/releasing.md`](docs/releasing.md) for the full process and required secrets.
