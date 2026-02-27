# Energomonitor Android App

A modern, native Android application for monitoring sensor and energy data from the Energomonitor platform. The app provides a clean dashboard to track real-time statistics like Energy, Temperature, Power, Gas, Water, Humidity, and CO2 levels.

*✨ Vibe coded within Antigravity IDE using the Gemini 3.1 Pro model.*

## Features

- **Secure Authentication:** Connect to the Energomonitor API using secure tokens.
- **Interactive Dashboard:** A Jetpack Compose powered main dashboard with categorize tabs for quick access to various sensor feeds.
- **Detailed Analytics:** Drill-down views showing detailed information for individual sensors and streams.
- **Home Screen Widgets:** Customizable temperature widgets (e.g., 1x1, 1x2, 2x1 boundaries) powered by Android Glance. Widgets are dynamically color-coded based on temperature values to provide at-a-glance insights directly from the home screen.
- **Background Sync:** Reliable widget data refreshes via WorkManager for up-to-date Home Screen statistics.
- **Modern UI:** Full Material 3 design support built entirely in Jetpack Compose.

## Tech Stack

The application leverages the absolute latest modern Android Development practices and libraries:

- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose & Material 3
- **Dependency Injection:** Hilt (Dagger)
- **Widgets:** Jetpack Glance AppWidgets
- **Networking:** Retrofit, OkHttp, & Kotlinx Serialization
- **Local Settings:** Preferences DataStore
- **Concurrency:** Kotlin Coroutines & Flow
- **Background Processing:** WorkManager
- **Navigation:** Jetpack Navigation Compose

## Contributing

This project is for personal use; I am not accepting Pull Requests at this time.
