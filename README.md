# DeepSeek Harness Android Launcher

Minimal Android shell for a DeepSeek Harness backend running inside Termux.

Expected Termux files:

- `~/dsh-android/start.sh`
- `~/dsh-android/stop.sh`

The app invokes Termux `RUN_COMMAND`, waits for `http://127.0.0.1:3080`, and then opens Harness in an in-app WebView.

## Required Termux setup

`~/.termux/termux.properties` must contain:

```properties
allow-external-apps=true
```

The launcher app also needs the Android additional permission **Run commands in Termux environment**.

## Build

The included GitHub Actions workflow builds a debug APK on `ubuntu-latest`.
