# 📄 Product Requirements Document (PRD)

## 1️⃣ Product Overview

### Working Name

**Shree Haridas**

### Vision

A minimal Android app that sends recurring audio notifications at user-defined intervals using custom or default sounds.

### Core Value

Simple, distraction-free repeating audio reminders.

---

# 2️⃣ Goals & Scope

## ✅ Goals (MVP)

* Set recurring notification in minutes
* Choose notification sound:

  * Record audio
  * Select local file
  * 2–3 built-in default sounds
* Play custom sound via app-specific notification channel
* Run reliably in background
* Optional override of Do Not Disturb (DND)

## ❌ Out of Scope (v1)

* Multiple timers
* Accounts / cloud sync
* Task management
* Calendar integration
* Advanced scheduling (weekdays/time windows)

---

# 3️⃣ Core Features

## 3.1 Frequency Configuration

* Input: integer (minutes)
* Min: 1 minute
* Max: 1440 minutes
* Default: 5 minutes
* Validation: positive integers only

User taps **Start** to begin recurring notifications.
Tap **Stop** to cancel.

---

## 3.2 Sound Selection

### Option A – Default Sounds

* 2–3 built-in sounds (e.g., bell, chime, beep)

### Option B – Record Sound

* Record using microphone
* Save locally
* Set as notification sound

### Option C – Select Local File

* Use system file picker
* Supported formats: MP3, WAV, OGG, M4A
* Persist URI safely (scoped storage)

---

## 3.3 Notification Behavior

* Uses dedicated Notification Channel
* Plays selected sound
* Optional vibration toggle
* Works when:

  * App closed
  * Screen locked
  * Device idle

Scheduling mechanism:

* Use **AlarmManager (setExactAndAllowWhileIdle)**
  (required for <15-minute intervals)

---

# 4️⃣ App Settings Screen

Settings accessible via top-right menu.

### Settings Options:

1. **Override Do Not Disturb (DND)**

   * Toggle: ON/OFF
   * When enabled:

     * App requests `Notification Policy Access`
     * Notifications allowed to bypass DND
     * Channel importance set to HIGH
   * If permission not granted:

     * Show system settings redirect

2. **Vibration Toggle**

   * Enable/Disable vibration

3. **Battery Optimization Guide**

   * Button: “Disable Battery Optimization”
   * Opens system settings

4. **Notification Volume Behavior**

   * Use system notification volume (default)

---

# 5️⃣ Permissions

Required:

* RECORD_AUDIO
* POST_NOTIFICATIONS (Android 13+)
* READ_MEDIA_AUDIO (Android 13+)
* SCHEDULE_EXACT_ALARM (Android 12+ if needed)
* ACCESS_NOTIFICATION_POLICY (for DND override)

Optional:

* REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

---

# 6️⃣ Functional Flow

### On Start:

1. Validate input
2. Save configuration
3. Schedule exact alarm
4. Show persistent “Reminder Active” status

### On Alarm Trigger:

1. Show notification
2. Play selected sound
3. Reschedule next alarm

---

# 7️⃣ State Persistence

Persist using DataStore:

* Selected sound URI
* Frequency value
* Running state
* DND override setting
* Vibration setting

App restores state on relaunch.

---

# 8️⃣ Non-Functional Requirements

### Performance

* App size < 15MB
* Low battery usage
* Trigger tolerance ±5 seconds

### Reliability

* No duplicate alarms
* Survive app kill
* Clear error handling if sound file missing

### Privacy

* No internet access
* Audio stored locally only

---

# 9️⃣ Edge Cases

* User deletes selected audio file
* DND override permission revoked
* Alarm permission denied
* Phone reboot (optional v1.1: reschedule on boot)
* Device in aggressive battery saver mode

---

# 🔟 MVP Definition

MVP includes:

* Frequency input
* Single active recurring reminder
* 3 default sounds
* Record custom sound
* Select audio file
* Background execution
* Settings screen (including DND override)
* Persistent state

---

# ⚠️ Key Technical Risks

1. Android background restrictions
2. Exact alarm permission handling (Android 12+)
3. DND override permission UX clarity

