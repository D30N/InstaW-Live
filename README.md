# InstaW Live

**InstaW Live** is an Android home-screen widget app that shows your Instagram follower count — live, right on your home screen.

![Platform](https://img.shields.io/badge/platform-Android-green) ![Min SDK](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-blue) ![License](https://img.shields.io/badge/license-MIT-lightgrey)

## Features

- 📊 **Live follower count** — big, glanceable number on your home screen
- 🔄 **Auto-refresh** — updates roughly every 15 minutes, plus tap-to-refresh
- 🌓 **Light & dark cards** — follows your system theme automatically
- 🎴 **Two card styles** — classic (followers row at the bottom) and name-on-top
- 📈 **Weekly growth pill** — green when you're growing, red when you dip
- 👤 **Avatar, name & verified badge** — your profile, faithfully rendered
- 🔐 **Private by design** — optional in-app Instagram login via WebView; your password is typed into Instagram's own page and never leaves your phone. Only the session cookie is kept in app-private storage. No data is sent anywhere else.

## Download

Grab the latest APK from the [**Releases**](../../releases) page and install it on your phone (Android 8.0+).

## Build from source

You need Android Studio (or the Android SDK + JDK 17) installed.

```bash
git clone https://github.com/D30N/InstaW-Live.git
cd InstaW-Live
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## How it works

- The widget fetches your public profile info from Instagram and caches the follower count on-device.
- A `WorkManager` job refreshes the count periodically and redraws the widget.
- Per-widget settings (username, card style, theme) are stored locally — different widgets can track different accounts and styles side by side.
- If Instagram rate-limits the fetch, the widget keeps showing the last known count instead of breaking.

## Disclaimer

This is an unofficial app and is not affiliated with, endorsed, or sponsored by Instagram or Meta. Instagram's endpoints may change at any time, which can affect fetching.

## License

MIT — see [LICENSE](LICENSE). © Deepak Deon
