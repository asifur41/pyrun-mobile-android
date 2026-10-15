# Contributing

1. Fork and create a branch.
2. Run `./gradlew testDebugUnitTest` before opening a PR. Instrumented tests (`PythonEngineTest`) need an arm64 device,
   or an emulator after adding `x86_64` to `abiFilters`.
3. Keep the app offline. A PR must not add:
   - any `<uses-permission>` entry
   - analytics, crash-reporting, ads or Play Services dependencies
   - network code (`HttpURLConnection`, OkHttp, WebView, ...)
4. Anything that runs in the `:python` process (`ExecutionService`, `OutputSink`, `runner.py`) must not reference UI
   classes or `PyRunApp`. `PythonEngine` is main-thread only.
5. `OutputSink` is called from Python by name; keep its public methods and its ProGuard keep rule.
