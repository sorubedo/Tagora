# Tagora

A smart task board that tells you what to do right now.

[中文版](README.zh-CN.md)

Define your daily routines, weekly schedules, or date-specific plans — Tagora automatically activates the right tags based on time, then shows which tasks are currently relevant using simple AND/OR/NOT logic.

## How It Works

1. **Create tags** (e.g., "Work", "Home", "Exercise")
2. **Set time periods** — daily (09:00–12:00), weekly (Mon–Fri), date range, or deadline
3. **Define tasks** with conditions like: *"Work" AND "Morning" but NOT "Meeting"*
4. The engine does the rest — your dashboard always shows what's active now

## Features

- **Auto-activating tags** based on time schedules
- **6-category dashboard** — Active, Deadline, Daily, Tomorrow, Weekly, Inactive
- **24-hour timeline** — visualize your day as colored blocks
- **Boolean task conditions** — AND/OR/NOT trees on tags
- **WebDAV backup** — automatic rotation, keeps latest 10
- **Background service** — persistent tag detection with notifications
- **Two presets** — General routine or student semester schedule

## Get Started

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Requires Android 8.0+ (API 26).

## License

[MIT](LICENSE)
