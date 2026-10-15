# PyRun Mobile

An offline Python editor and runner for Android 8.0+. Write Python, tap ▶, see the output. No account, no network, no analytics.

## Features

- Full CPython 3.11 standard library (via [Chaquopy](https://chaquo.com/chaquopy/))
- Syntax highlighting, line numbers, auto-indent, local autocomplete, symbol bar
- Projects with multiple `.py` files; `import helper` works between files in a project
- Hard limits per run: **30 s** wall-clock, **150 MB** memory growth (best-effort)
- Stop button that really stops: Python runs in its own process, which is killed on stop/timeout
- Import/export `.py` files through the system file picker (no storage permissions)
- No `INTERNET` permission; network calls from Python raise `OSError`

## Build

Requirements: JDK 17, Android SDK (API 34), Python 3 on `PATH` (Chaquopy needs it at build time).

```bash
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/build-apk.yml`) does the same on every push and uploads the debug APK as an artifact.
A signed release APK is built on `main` when the repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD` are set.

The app is `arm64-v8a` only to keep the APK small. To run on an x86_64 emulator, change `abiFilters` in
`app/build.gradle.kts` to `listOf("arm64-v8a", "x86_64")`.

## Architecture

```
 UI process                                   :python process
 CodeEditorActivity -> RuntimeExecutor        ExecutionService
        -> PythonEngine --Messenger--------->   runner.py (exec user code)
           TimeoutGuard 30 s  <----output----   OutputSink, memory watchdog
 on timeout/stop: kill the :python process, rebind on next run
```

## Limitations

- `input()` is not supported yet (stdin is empty, so it raises `EOFError`)
- No network, no GUI libraries (`tkinter`), no third-party packages by default
  (add them in `chaquopy { defaultConfig { pip { install("...") } } }` in `app/build.gradle.kts`)
- The memory limit is polled every 500 ms, so a very fast allocation can overshoot before it is stopped

## License

MIT, see [LICENSE](LICENSE).
