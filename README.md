# Deiak Water Network (Android)

## Overview
Deiak Water Network is my CS50 final project—an Android app for mapping and managing water network infrastructure in Corfu, Greece. It lets users view nodes (e.g., valves, hydrants) and pipes on a Google Map, while admins can add, edit, or delete them. It connects to a Node.js and MongoDB backend (see separate repo).

## Features
- Add, edit, move, and delete nodes with details like name and status.
- Draw, edit, and delete pipes with flow direction and length.
- Interactive map with zoom, filters, and Corfu bounds.
- Admin-only editing; regular users view only.

## Tech Used
- **Language**: Kotlin
- **UI**: Android Views (XML)
- **Networking**: Retrofit
- **Concurrency**: Kotlin Coroutines
- **Mapping**: Google Maps SDK
- **Backend**: Node.js + MongoDB (separate repo)

## Setup
1. **Clone It**
   ```bash
   git clone https://github.com/NikosKourkoulos1/DEIAK-Android-App.git
   cd DeiakWaterNetwork-Android
