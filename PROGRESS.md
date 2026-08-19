# VeNdO — Progress / Handoff Notes (specific version)

Backend: `C:\voiceorder` (existing repo, git-tracked, on branch `master`). Android app: `C:\VeNdO` (**new directory, NOT yet a git repo** — `git init` was never run here; do that before committing anything).

Full original plan: `C:\Users\anisw\.claude\plans\i-need-to-transform-cosmic-flask.md`.

---

## ⏭️ RESUME HERE — exact commands, in order

```powershell
# 1. Install JDK 17 (previous attempt died mid-download when the machine
#    shut down — winget does not resume, this restarts from 0 bytes)
winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements --silent

# 2. Verify java is on PATH (may need a new terminal for PATH to refresh)
java -version

# 3. SDK command-line tools are ALREADY installed at C:\Android\Sdk\cmdline-tools\latest
#    ANDROID_HOME=C:\Android\Sdk is ALREADY set as a User env var.
#    Confirm both still true:
Test-Path "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat"   # should be True
[Environment]::GetEnvironmentVariable("ANDROID_HOME", "User")        # should print C:\Android\Sdk

# 4. Accept licenses, install the remaining SDK packages (none of this is done yet)
& "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
& "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;25.2.9519653"

# 5. Build (first run will ALSO download the Gradle distribution + Maven
#    deps — more downloads, budget time for this on a slow connection)
cd C:\VeNdO
.\gradlew build
```

Then fix whatever real compile errors `gradlew build` reports. Start with `:whisper:externalNativeBuildDebug` / the CMake configure step first — that's the highest-risk, never-compiled part (see "Milestone 3 risk list" below). Once native + Kotlin compile, do a full app run against the backend (Milestone 5 section below has the exact backend-startup commands).

---

## Toolchain state at last shutdown (exact)

| Item | State | Path |
|---|---|---|
| Android SDK cmdline-tools | ✅ Downloaded (143,040,480 bytes) + extracted | `C:\Android\Sdk\cmdline-tools\latest\` |
| `ANDROID_HOME` env var | ✅ Set (User scope) | `C:\Android\Sdk` |
| JDK 17 (Temurin) | ❌ Killed at ~121MB / ~190MB (~65%), process 18372 stopped | was downloading to `C:\Users\anisw\AppData\Local\Temp\WinGet\EclipseAdoptium.Temurin.17.JDK.17.0.20.8\` |
| `platform-tools`, `platforms;android-35`, `build-tools;35.0.0` | ❌ Not installed | — |
| `ndk;25.2.9519653` | ❌ Not installed (pinned in `whisper/build.gradle.kts` line 27) | — |
| SDK licenses | ❌ Not accepted | — |
| `gradlew build` | ❌ Never run | — |
| Measured connection speed | ~0.4 Mbps (~30-60KB/s), confirmed via a 10MB Cloudflare speed-test download | — |

Download source used for the SDK zip: `https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip` (this exact build number 404'd nowhere else tried — `edgedl.me.gvt1.com` mirror 404'd, use `dl.google.com`).

---

## ✅ Milestone 1 — Backend (`C:\voiceorder`) — DONE, tested, verified live

Every file below was created or edited this session. (The repo also has a large number of *other* untracked/modified files from unrelated prior work already in progress before this session started — do not confuse those with this list; `git status` in `C:\voiceorder` will show both mixed together.)

**New files:**
| File | Purpose |
|---|---|
| `app/models/salesman.py` | `Salesman` ORM model — table `salesman`, PK `login_id: String(50)`, `password_hash`, `name`, `email`, `is_active`, `created_at`. |
| `alembic/versions/f86ca96e8728_add_salesman.py` | Migration creating the `salesman` table. `down_revision = '93a471f20197'`. |
| `alembic/versions/a3b7d1c9f204_voice_transcript_source.py` | Migration adding `voice_message.transcript_source` (`String(20)`, default `"server"`). `down_revision = 'f86ca96e8728'`. Current alembic head after both = `a3b7d1c9f204`. |
| `app/services/auth.py` | `hash_password`/`verify_password` (bcrypt, direct — not passlib, to avoid passlib/bcrypt 4.x version-detection issues), `create_token`/`decode_token` (PyJWT, HS256). |
| `app/schemas/auth.py` | Pydantic: `LoginIn`, `RegisterIn`, `ChangePasswordIn`, `AccountUpdateIn`, `SalesmanOut`, `LoginOut`. |
| `app/api/auth.py` | Router: `POST /auth/login`, `POST /auth/register` (API-key-gated via `Depends(require_api_key)` explicitly, regardless of global setting), `GET /auth/me`, `PATCH /auth/me`, `POST /auth/change-password`. |
| `seed_salesman.py` (repo root) | `python seed_salesman.py <login_id> <password> <name> [email]`; no args → upserts `demo`/`demo1234`/"Demo Salesman"/demo@example.com. |

**Modified files (exact line anchors as of this writing — re-check with `grep -n` if the file has moved since, line numbers drift):**
| File | Line(s) | Change |
|---|---|---|
| `app/models/__init__.py` | full file | Added `from app.models.salesman import Salesman`, added `"Salesman"` to `__all__`. |
| `app/config.py` | ~76-85 | Removed `operators: list[str] = []` (no longer used — see deps.py rewrite). Added `jwt_secret: str` (**required, no default** — must be in `.env`), `jwt_algorithm: str = "HS256"`, `jwt_expire_minutes: int = 20160`. |
| `app/api/deps.py` | line 32 | `require_api_key` unchanged. |
| | line 45 | New `get_current_salesman(authorization, s)` — parses `Bearer <token>`, calls `decode_token`, loads `Salesman` by `login_id`, checks `is_active`, 401 on any failure. |
| | line 67 | `get_operator` rewritten to `Depends(get_current_salesman)`, returns `salesman.login_id`. **The old free-text `X-Operator` header is completely gone** — anything still sending it will 401. |
| `app/main.py` | line 10 | Import list gained `auth`. |
| | line 29 | `app.include_router(auth.router, dependencies=_guard)` added after the other 4 routers. |
| `app/models/voice.py` | after the `status` column | Added `transcript_source: Mapped[str] = mapped_column(String(20), default="server")`. |
| `app/api/ingest.py` | line 32-33 | `ingest_voice` signature gained `transcript: str \| None = Form(default=None)`, `language: str \| None = Form(default=None)`. Body sets `vm.transcript`/`vm.transcript_source = "client_whisper"`/`vm.language` when `transcript` is non-blank. Audio is still always saved. |
| `app/pipeline.py` | line 55 | `IntakePipeline.process` branches: `if voice.transcript_source == "client_whisper" and voice.transcript:` builds a `Transcript` object directly (`quality="good"`, `confidence=1.0`, `normalized_transcript` via `normalize_text`) instead of calling `self.stt.transcribe(...)`. |
| `app/services/commit.py` | line 85-89 | `log_activity(..., "order_committed", ..., details={"operator": operator, "order_type": order_type, "primary_intent": req.primary_intent})` — the `"primary_intent"` key is new; this is what the Android LOG QUERY screen reads. |
| `requirements.txt` | end of file | Added `bcrypt==4.2.1`, `PyJWT==2.10.1`. |
| `.env` / `.env.example` | end of file | Added `JWT_SECRET=` (real value in `.env`, generated via `python -c "import secrets;print(secrets.token_hex(32))"`, blank placeholder + comment in `.env.example`). |
| `tests/test_queue_review.py` | top of file | Added `_ensure_salesman()` helper + module-level `TOKENS = {"alice": <jwt>, "bob": <jwt>}` built via `create_token()` directly (no HTTP round-trip). All 27 occurrences of `headers={"X-Operator": "alice"}` / `"bob"` replaced with `headers={"Authorization": f"Bearer {TOKENS['alice']}"}` / `['bob']`. `test_claim_missing_operator_header_400` renamed to `test_claim_missing_bearer_token_401` (assertion changed 400→401), added `test_claim_invalid_bearer_token_401`, added `test_client_whisper_transcript_skips_server_stt` (asserts `stt.transcribe` is never called when `transcript_source="client_whisper"`). |

**Test results**: `.venv\Scripts\python -m pytest tests/ -q` → **226 passed**. (Required installing `bcrypt`, `PyJWT`, `openpyxl` into `C:\voiceorder\.venv` first — all three now present.) Both the dev DB and the `voiceorder_test` schema had `alembic upgrade head` run against them (dev DB: `DATABASE_URL` from `.env`; test schema: `DATABASE_URL` overridden with `?options=-c%20search_path%3Dvoiceorder_test%2Cpublic`, set automatically by `tests/conftest.py`).

**Live curl verification performed** (dev server on port 8123, since torn down — this was a one-off manual check, not a running service):
1. `python seed_salesman.py` → created `demo`/`demo1234`.
2. `POST /auth/login` with `{"login_id":"demo","password":"demo1234"}` → got a token, confirmed `GET /auth/me` with `Authorization: Bearer <token>` returns the right salesman.
3. `POST /ingest/voice` with a synthetic silent WAV + `transcript="place order for test trading items blue paint quantity two the end"` (a phrase matching the scripted grammar in `app/services/scripted/anchor_phrases.json`) + `language=en` → `voice_id=562`.
4. Ran `python -m app.worker` briefly → voice 562 reached `status=drafted`, `request_id=501`, customer resolved to `C001`/"Test Trading", `primary_intent=add_order` — **confirming Gemini was never called** (worker log just said "processed 561"/"processed 562", no Gemini API traffic).
5. `POST /requests/501/accept` with the bearer token and `{"order_type":"SO","lines":[{"line_nb":1,"item_nb":"A100","qty":"2"}],"removed_line_nbs":[]}` → order `260000021` committed.
6. `GET /activity?event_type=order_committed&limit=5` → confirmed the new row's `details` includes `"primary_intent":"add_order"` (an older pre-fix row in the same response correctly lacks that key, confirming the fallback logic on the Android side is needed for historical rows).

Test artifacts left in the dev DB from this: voice messages 561/562, request 500/501, order `260000021`/SO for customer `C001`. Harmless — `C001`/"Test Trading" is the same seeded demo customer already used by `seed_test.py` and pre-existing order `260000020`.

### Running the backend
```powershell
cd C:\voiceorder
.venv\Scripts\python -m uvicorn app.main:app --port 8000
# separate terminal:
.venv\Scripts\python -m app.worker
# once, to create the demo login:
.venv\Scripts\python seed_salesman.py
```

---

## ✅ Milestone 2 — Android scaffold (`C:\VeNdO`) — files written, NEVER COMPILED

Full file tree (excluding the ~1291 vendored whisper.cpp source files under `whisper/src/main/cpp/whisper.cpp/{src,include,ggml}/`, listed separately below):

```
C:\VeNdO\
  settings.gradle.kts              — includes :app, :core:designsystem, :core:datastore, :core:network, :whisper
  build.gradle.kts                 — root: AGP 8.6.1, Kotlin 2.0.21, Hilt 2.52, KSP 2.0.21-1.0.28, kotlinx-serialization 2.0.21
  gradle.properties
  gradle\wrapper\gradle-wrapper.properties   — distributionUrl = gradle-8.9-bin.zip (the actual gradle-wrapper.jar binary was NEVER generated — Gradle/Android Studio must create it on first sync/run, or run `gradle wrapper` manually if a system Gradle is available)
  .gitignore
  PROGRESS.md                      — this file

  app\                             (package com.vendo.app, applicationId com.vendo.app, minSdk 24, compileSdk/targetSdk 35)
    build.gradle.kts
    src\main\AndroidManifest.xml   — permissions: INTERNET, RECORD_AUDIO. No custom launcher icon (system default used — @mipmap/ic_launcher was deliberately NOT referenced to avoid a missing-resource build error).
    src\main\res\values\strings.xml
    src\main\java\com\vendo\app\
      VendoApplication.kt          — @HiltAndroidApp
      MainActivity.kt              — @AndroidEntryPoint, hosts VendoTheme + VendoNavGraph
      AppViewModel.kt              — theme mode, SessionState (Loading/LoggedOut/LoggedIn), authEvents (AuthEventBus passthrough), toggleTheme(), logOut()
      navigation\
        VendoDestinations.kt       — route constants: login, record, request?requestId={requestId} (default -1 = "most recent"), logquery, menu, menu/account, menu/changepassword
        VendoNavGraph.kt           — ModalNavigationDrawer + NavHost, forced-logout on AuthEvent.LoggedOut via navController.graph.id popUpTo
      login\LoginScreen.kt, LoginViewModel.kt
      record\RecordScreen.kt, RecordViewModel.kt   — see Milestone 3 below, this is the whisper.cpp integration point
      request\RequestScreen.kt, RequestViewModel.kt
      logquery\LogQueryScreen.kt, LogQueryViewModel.kt
      menu\MenuScreen.kt, AccountScreen.kt, AccountViewModel.kt, ChangePasswordScreen.kt, ChangePasswordViewModel.kt

  core\designsystem\               (package com.vendo.core.designsystem)
    build.gradle.kts
    src\main\java\com\vendo\core\designsystem\
      Color.kt        — VendoPrimaryBlue #4F78D9, VendoDarkBlue #2F5F88, VendoWhite, VendoLightGray #E9ECEF, VendoGray #CFCFCF, VendoDarkGray #5A5A5A, VendoBlack, dark-mode-only VendoDarkBackground #202124/VendoDarkSurface #252525/VendoDarkSurfaceAlt #333333
      Theme.kt         — VendoThemeMode{LIGHT,DARK} enum (explicit toggle, no SYSTEM option), VendoTheme() composable wrapping MaterialTheme with two full ColorScheme objects
      Type.kt          — VendoTypography; FontFamily.SansSerif + FontWeight.Black/ExtraBold as the closest stock approximation of the reference's bold/condensed display font (NO real matching font was ever identified — see Milestone 4)
      components\
        VendoTopBar.kt      — hamburger (custom Canvas-drawn 3-line icon) / centered "VeNdO" / sun-moon theme toggle, 56dp height
        PillButton.kt       — PillVariant{PrimaryBlue,DarkBlue,DarkGray}, RoundedCornerShape(percent=50)
        RequestCard.kt      — 2dp VendoPrimaryBlue border, RoundedCornerShape(20dp)
        LogListItem.kt      — rounded 10dp, MaterialTheme.colorScheme.surfaceVariant background
        MenuListItem.kt     — always-white row, always-black text (independent of app theme — sits on the permanently-blue MENU background)
        VendoDrawer.kt      — DrawerDestination(label, route), plain ModalDrawerSheet + NavigationDrawerItem list, no icons

  core\datastore\                  (package com.vendo.core.datastore)
    build.gradle.kts
    src\main\java\com\vendo\core\datastore\SettingsDataStore.kt
      — DataStore<Preferences> named "vendo_settings"; keys: dark_mode (Boolean), auth_token, login_id, salesman_name (String)

  core\network\                    (package com.vendo.core.network)
    build.gradle.kts                — BuildConfig.BASE_URL = "http://10.0.2.2:8000/" (emulator→host alias; CHANGE to your LAN IP, e.g. "http://192.168.1.20:8000/", for a physical device)
    src\main\java\com\vendo\core\network\
      ApiService.kt        — Retrofit interface, one method per backend endpoint used
      AuthEventBus.kt      — sealed AuthEvent{LoggedOut}, SharedFlow
      AuthInterceptor.kt   — injects "Authorization: Bearer <token>" on every request except paths starting "auth/login" or "health"; on any 401 response (excluding those two paths), clears the stored session and emits AuthEvent.LoggedOut
      NetworkModule.kt     — Hilt @Module providing OkHttpClient (with AuthInterceptor + HttpLoggingInterceptor at BASIC level), Retrofit (kotlinx.serialization JSON converter, ignoreUnknownKeys=true, explicitNulls=false), ApiService
      dto\
        AuthDto.kt         — LoginIn, SalesmanOut, LoginOut, AccountUpdateIn, ChangePasswordIn
        QueueDto.kt        — QueueRow (duration_sec: String? — backend Decimal fields serialize as JSON STRINGS, confirmed via the live curl test, e.g. "0.10" not 0.10)
        RequestDto.kt      — TranscriptSegment, CandidateOut, LineOut (qty: String?, same Decimal-as-string caveat), RequestDetail, LineEditIn, AcceptIn, AcceptOut, RejectIn, CallbackIn, OkOut
        ActivityDto.kt     — ActivityLogOut (details: Map<String, JsonElement>), logQueryVerb()/logQueryLine() extension functions mapping details["primary_intent"] → "ADD ORDER"/"REORDER"/"RETURN ORDER"/"ORDER" fallback
        IngestDto.kt        — IngestVoiceOut, VoiceStatusOut

  whisper\                         (package com.vendo.whisper — see Milestone 3, full detail below)
```

Total non-vendored Kotlin/Gradle source: `app/` 126KB, `core/` 101KB, `whisper/` (excluding vendored whisper.cpp) 56KB.

**Login/session flow implemented exactly as**: `SettingsDataStore.loginId` → `AppViewModel.sessionState` (Loading until first DataStore emission, to avoid flashing the Login screen for an already-logged-in user) → `VendoNavGraph` picks `startDestination` = `RECORD` if `LoggedIn`, else `LOGIN`.

**Backend URL**: hardcoded in `core/network/build.gradle.kts` as a `buildConfigField`. To point at a real device instead of the emulator, edit that one line and change `10.0.2.2` to the backend machine's actual LAN IP.

---

## ✅ Milestone 3 — whisper.cpp integration — files written, NEVER COMPILED, HIGHEST RISK

Source: `C:\Users\anisw\Downloads\whisper.cpp-master.zip` (10,844,673 bytes, downloaded 2026-08-18 14:46:23, GitHub "Download ZIP" of the `master` branch — confirmed **not** a git submodule stub, `ggml/` came fully populated with 1244 files, so nothing is missing).

**Vendored into `C:\VeNdO\whisper\src\main\cpp\whisper.cpp\`** (19.46 MB, 1291 files) — copied from the extracted zip: top-level `CMakeLists.txt`, `LICENSE`, `src/` (5 files: `whisper.cpp`, `whisper-arch.h`, `CMakeLists.txt`, `parakeet.cpp`, `parakeet-arch.h`), `include/` (2 files: `whisper.h`, `parakeet.h`), `ggml/` (full: `cmake/`, `include/`, `src/`). **NOT copied**: `examples/`, `tests/`, `bindings/`, `models/`, `grammars/`, `samples/`, `.github/`, `.devops/`, docs.

**JNI bridge** — adapted line-by-line from the real upstream reference at `examples/whisper.android/lib/src/main/jni/whisper/{CMakeLists.txt,jni.c}` inside that same zip (I read and copied from it directly, not from memory):
- `C:\VeNdO\whisper\src\main\cpp\CMakeLists.txt` — identical build logic to upstream (per-ABI targets `whisper_v8fp16_va` for arm64-v8a, `whisper_vfpv4` for armeabi-v7a, always also builds a fallback `whisper` target for any ABI). **One structural change**: `WHISPER_LIB_DIR` is `${CMAKE_SOURCE_DIR}/whisper.cpp` (upstream's is `${CMAKE_SOURCE_DIR}/../../../../../../..` because their sample nests 7 directories deep inside the actual whisper.cpp repo; this project vendors a flat copy right next to the CMakeLists.txt instead).
- `C:\VeNdO\whisper\src\main\cpp\jni.c` — same 9 JNI functions as upstream (`initContextFromInputStream`, `initContextFromAsset`, `initContext`, `freeContext`, `fullTranscribe`, `getTextSegmentCount`, `getTextSegment`, `getSystemInfo`) **minus** `getTextSegmentT0`/`T1` and the two `bench*` functions (dropped — not needed, and dropping them from both jni.c and LibWhisper.kt together avoids a Kotlin/JNI symbol mismatch) **plus** a new `getDetectedLanguage` (calls `whisper_full_lang_id()` + `whisper_lang_str()`, both confirmed present in the vendored `whisper.h` at lines 634 and 367). Every exported symbol renamed from `Java_com_whispercppdemo_whisper_WhisperLib_00024Companion_*` (upstream's demo app package) to `Java_com_vendo_whisper_WhisperLib_00024Companion_*` — **this exact string must match `com.vendo.whisper.WhisperLib`'s package+class name on the Kotlin side or every native call throws `UnsatisfiedLinkError` at runtime** (not a compile-time error — this is the single highest-risk spot in the whole Android project). The **one functional change** from upstream: `fullTranscribe` takes a 5th parameter `jstring language_str` (upstream hardcodes `params.language = "en"`); this project passes `"auto"` by default so whisper.cpp language-detects per utterance instead of forcing English, since target speech is Arabic/Arabizi/English mixed. `params.translate` stays `false` either way (never translate to English).

**Kotlin bridge**:
- `C:\VeNdO\whisper\src\main\java\com\vendo\whisper\LibWhisper.kt` — `WhisperContext` (public: `createContextFromFile`/`createContextFromInputStream`/`createContextFromAsset`/`getSystemInfo`, instance `transcribeData(FloatArray, language="auto"): TranscribeResult`, `release()`), private `WhisperLib` object with the `external fun` declarations matching jni.c's 7 remaining exports + `System.loadLibrary` CPU-variant-detection logic (reads `/proc/cpuinfo` to decide `whisper_vfpv4` vs `whisper_v8fp16_va` vs plain `whisper`).
- `C:\VeNdO\whisper\src\main\java\com\vendo\whisper\WhisperCpuConfig.kt` — thread-count heuristic, copied near-verbatim from upstream.
- `C:\VeNdO\whisper\src\main\java\com\vendo\whisper\WhisperEngine.kt` — `WhisperEngine` interface (`isModelLoaded`, `loadModel(File)`, `transcribe(ShortArray): WhisperResult`) + `WhisperCppEngine` implementation: converts 16-bit PCM `ShortArray` to `FloatArray` via `pcm[i] / 32768.0f` (whisper.cpp requires float32 samples in `[-1, 1]`, **not** int16 — this conversion is mandatory, whisper_full will silently produce garbage/crash on raw int16 data passed as float bit patterns if this step is ever removed).
- `C:\VeNdO\whisper\src\main\java\com\vendo\whisper\WhisperModule.kt` — Hilt `@Module`: `provideWhisperEngine()` returns `WhisperCppEngine()` (real implementation — the old `WhisperEngineStub` class has been deleted entirely, `grep -rn WhisperEngineStub C:\VeNdO` returns nothing except this file's own history), `provideModelManager(context)`.

**Audio capture** (`C:\VeNdO\whisper\src\main\java\com\vendo\whisper\AudioRecorder.kt`) — `AudioRecorder` (real `android.media.AudioRecord`, `WHISPER_SAMPLE_RATE = 16000`, mono, 16-bit PCM, suspend `record(): ShortArray` loop until `stop()` is called) + `WavWriter.write(ShortArray, sampleRate): ByteArray` (44-byte RIFF/WAVE header + raw PCM, used both for the local WAV upload and, converted to float, for whisper.cpp itself — the same captured buffer serves both purposes).

**Model download** (`C:\VeNdO\whisper\src\main\java\com\vendo\whisper\ModelManager.kt`):
- `MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"`
- `MODEL_SHA256 = "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b"`
- File size per the Hugging Face API (`/api/models/ggerganov/whisper.cpp/tree/main`) at write time: **487,601,967 bytes** (~465MB).
- Both values came from live API calls this session (`huggingface.co/api/models/...tree/main`, the `oid` field of the `lfs` object for `ggml-small.bin`), not guessed — but **never verified against an actual downloaded file** (that download itself would take ~2-4 hours at this connection's measured speed, so it wasn't attempted this session). Re-verify the checksum the first time a real device actually downloads it.
- Downloads to `context.filesDir/models/ggml-small.bin`, with a `.part` temp file renamed on success, SHA-256 verified via streaming `MessageDigest` before the rename.

**`whisper/build.gradle.kts`** exact current state: `ndkVersion = "25.2.9519653"` (pinned, must be installed via `sdkmanager` — see resume commands above), `externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }` (uncommented), `abiFilters` = `arm64-v8a` + `armeabi-v7a` always, `+x86_64` in debug builds only (for the emulator).

**`RecordViewModel.kt`/`RecordScreen.kt` model-state UI**: `ModelState` sealed interface (`Checking`/`NotDownloaded`/`Downloading(bytes,total)`/`Loading`/`Ready`/`Failed(message)`). Tapping the record button when `NotDownloaded` or `Failed` triggers `downloadModel()` instead of recording; the button shows a spinner and is disabled while `Checking`/`Downloading`/`Loading`; caption text under the button reflects the exact state (see `recordCaption()` in `RecordScreen.kt`).

### Milestone 3 risk list — check these first when the build finally runs
1. **CMake configure step**: does `FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)` correctly resolve the vendored copy? (`WHISPER_LIB_DIR` = `whisper/src/main/cpp/whisper.cpp`, so this should be `whisper/src/main/cpp/whisper.cpp/ggml` — exists, verified via `Test-Path` this session.)
2. **Version regex**: the vendored `whisper.cpp/CMakeLists.txt` line 2 is `project("whisper.cpp" C CXX)` — **no VERSION clause** (this whisper.cpp snapshot dropped it). The JNI CMakeLists.txt's regex won't match, `WHISPER_VERSION` falls back to `"unknown"` — this is handled gracefully (just a compiled-in string), confirm it doesn't hard-fail the configure step.
3. **`ggml` CMake minimum version**: vendored `ggml/CMakeLists.txt` declares `cmake_minimum_required(VERSION 3.14...3.28)`; the JNI wrapper's own `CMakeLists.txt` declares `3.10`. Not expected to conflict (AGP's bundled CMake is 3.22+), but confirm no policy warning becomes an error.
4. **JNI symbol linkage** (the big one): confirm `Java_com_vendo_whisper_WhisperLib_00024Companion_*` in `jni.c` actually matches what the JVM looks for at runtime given `com.vendo.whisper.WhisperLib`'s real compiled name — a build succeeding does NOT prove this; only actually calling `WhisperContext.createContextFromFile(...)` on a device/emulator proves it (compile-time success + runtime `UnsatisfiedLinkError` is the expected failure mode if something's wrong here, not a build error).
5. **`System.loadLibrary` ABI selection logic** in `LibWhisper.kt` — reads `/proc/cpuinfo`, decides between `whisper_vfpv4`/`whisper_v8fp16_va`/`whisper` — confirm the actual `.so` files produced by the CMake build are named to match (`libwhisper_v8fp16_va.so`, `libwhisper_vfpv4.so`, `libwhisper.so` — CMake's `add_library(${target_name} SHARED ...)` should produce exactly these given the target names in `CMakeLists.txt`, but confirm in the APK's `lib/<abi>/` folder after a build).

---

## 🚧 Milestone 4 — Pixel-accurate UI — PARTIAL, never visually verified

All 5 screens + Account/ChangePassword exist and use the spec's exact palette (hex values in `Color.kt`, listed above), pill buttons, the vertical white/blue split login background, the blue-bordered rounded Request card, the dense light-gray Log Query list, the blue-dominant Menu screen. **This session had zero ability to render a Compose preview or run an emulator** — nothing here has been visually compared to the original reference image.

Specific known gaps:
- **Font**: no real match for the reference's bold/condensed display typeface was ever identified. `core/designsystem/Type.kt` uses `FontFamily.SansSerif` + `FontWeight.Black`/`ExtraBold` as a stand-in, with a code comment explaining how to swap in a real bundled `.ttf` (drop it in `app/src/main/res/font/`, change `VendoDisplayFamily` in `Type.kt`).
- **Spacing/proportions**: implemented from the written spec's descriptions (dp values chosen by hand: 56dp top bar, 20dp card corner radius, 12dp menu item corner radius, etc.) — not measured against the reference image pixel-by-pixel.
- **Dark mode**: both `ColorScheme`s exist in `Theme.kt` and the toggle is wired end-to-end (`VendoTopBar` → `AppViewModel.toggleTheme()` → `SettingsDataStore.setDarkMode()` → persisted, `MainActivity`'s `VendoTheme(mode=...)` wraps the whole nav graph) but has never been looked at.

---

## ⬜ Milestone 5 — End-to-end + polish — NOT STARTED

Needs: a compiled, running app (blocked on the toolchain, see top of this file) + a running backend (`uvicorn` + `app.worker`, commands above) + a physical Android device or emulator with network access to the backend's port 8000 + the real whisper.cpp model actually downloaded on-device (a ~465MB download, budget real time for this on a real device too if its connection is also slow).

---

## ⚠️ Assumptions made without the user's explicit sign-off — revisit these

1. **No phone-number field on the Record screen.** `POST /ingest/voice` requires a `phone` form field but the reference UI shows none. `RecordViewModel.submit()` sends `"salesman:$loginId"` as a placeholder. The scripted grammar (`app/services/scripted/`) actually resolves the customer from the *spoken name* in the transcript text, not from this field, so it's cosmetic/audit-only for now — but confirm the app's real intended usage pattern (salesman transcribing a live phone call vs. salesman dictating an order while on-site) before shipping.
2. **Accept vs Draft button semantics on Record** (`RecordViewModel.submit(accept: Boolean)`): `Accept` submits, polls `GET /ingest/voice/{id}` for up to 15 seconds, then navigates to the Request screen once `request_id` appears. `Draft` submits and just clears the Record screen, leaving the request to be picked up later via Log Query/queue. This is my interpretation — the reference image doesn't define what these two buttons are supposed to do differently on the backend.
3. **Item resolution in Request-screen EDIT mode**: no item search/picker UI exists (matches the reference's minimal card design). An unresolved line (`item_nb == null`) can only be fixed by tapping one of the resolver's pre-computed `candidates` if any exist (`RequestViewModel.selectCandidate()`); if `candidates` is empty (as it was in the live curl test for "blue paint" — zero fuzzy matches), there is currently no way to resolve that line from the Android UI at all, only free-text edits to quantity/description which won't satisfy the backend's "every line needs item_nb + qty" commit requirement.

---

## Quick reference

- Backend repo: `C:\voiceorder` (branch `master`, has other unrelated in-progress uncommitted work mixed in from before this session — see `git status`)
- Android repo: `C:\VeNdO` (**not a git repo yet** — run `git init` before committing)
- Full original plan doc: `C:\Users\anisw\.claude\plans\i-need-to-transform-cosmic-flask.md`
- Demo login: `demo` / `demo1234` (after running `python seed_salesman.py` against whichever DB the backend is pointed at)
- Alembic head (both dev DB and `voiceorder_test` schema): `a3b7d1c9f204`
- Android → backend URL: `http://10.0.2.2:8000/`, hardcoded in `core/network/build.gradle.kts`'s `buildConfigField("String", "BASE_URL", ...)` — the only place to change it for a physical device
