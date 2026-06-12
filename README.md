# BitStreamNews 📡

> **8-bit Retro Pixel Art News App for Android**  
> A fully offline-capable news reader with a classic CRT / BIOS aesthetic, built as a university mobile programming laboratory project.

---

## 🇬🇧 Description (English)

BitStreamNews is an Android news application that fetches the latest headlines from the internet via **NewsAPI.org** and caches them locally in an SQLite database for fully offline reading. The entire UI is designed in a retro **8-bit pixel art** style — inspired by classic CRT terminals, BIOS setup screens, and early 2D video games — rendered using the **Press Start 2P** bitmap font throughout.

The app supports both a **Light Theme** (aged parchment / amber CRT) and a **Dark Theme** (classic phosphor green on black), switchable at runtime without restarting.

---

## 🇮🇩 Deskripsi (Bahasa Indonesia)

BitStreamNews adalah aplikasi berita Android yang mengambil headline terbaru dari internet melalui **NewsAPI.org** dan menyimpannya secara lokal di database SQLite untuk dibaca saat offline. Seluruh tampilan dirancang dengan gaya **pixel art 8-bit retro** — terinspirasi dari terminal CRT klasik, layar setup BIOS, dan game 2D lawas — menggunakan font bitmap **Press Start 2P** di seluruh antarmuka.

Aplikasi mendukung **Tema Terang** (perkamen tua / amber CRT) dan **Tema Gelap** (hijau fosfor klasik di atas hitam), yang dapat diganti saat runtime tanpa restart aplikasi.

---

## 🎮 Features

- **Retro Pixel Art UI** — Press Start 2P font, zero rounded corners, pixel borders, CRT color palettes
- **Cache-First Architecture** — SQLite served first; API only called on first launch or explicit Refresh (quota: 100 req/day)
- **Offline Support** — Full news list readable with zero internet connection once cached
- **Dual Theme** — Light (Parchment/Amber CRT) and Dark (Phosphor Green CRT), persisted in SharedPreferences
- **Bookmark / Saved News** — Save any article locally with one tap; accessible from the Saves tab
- **In-App Search** — Real-time filter with zero API calls (operates on in-memory cache)
- **GAME OVER Error Screen** — Pixel-art error state with RETRY button when both network and cache fail
- **Share + Open in Browser** — Share article URL or open full article in the system browser

---

## 🏗️ Technical Implementation (Lab Specification)

### ✅ Lab Checklist

| Requirement | Implementation |
|---|---|
| **2 Activities** | `MainActivity` (Launcher) + `NewsDetailActivity` |
| **Intent with data** | `MainActivity.launchNewsDetail(article)` passes 8 article fields as extras |
| **RecyclerView (news list)** | `HomeFeedFragment` — vertical list with `NewsAdapter` + `DiffUtil` |
| **RecyclerView (saved list)** | `BookmarkFragment` — vertical list of bookmarked articles |
| **3 Fragments** | `HomeFeedFragment`, `BookmarkFragment`, `SettingsFragment` |
| **Navigation Component** | `nav_graph.xml` — 3 destinations, 6 actions, pixel slide animations |
| **Background Threading** | `ExecutorService` (single thread) for all DB + network ops; `Handler(Looper.getMainLooper())` for UI callbacks |
| **Retrofit** | `NewsApiService` (Retrofit interface) + `ApiClient` (OkHttpClient with `X-Api-Key` header interceptor) |
| **Refresh Button** | Toolbar `[↺]` in `HomeFeedFragment` + RETRY button in error state — both call `NewsRepository.refreshNews()` |
| **SQLite Caching** | `NewsDbHelper` (SQLiteOpenHelper v2) — INSERT OR REPLACE, WAL mode, indexes on category + cached\_at, `is_bookmarked` column |
| **SharedPreferences** | Theme mode (`"app_prefs"` → `"theme_mode"`) persisted and read before `setContentView` in MainActivity |
| **Light Theme** | `res/values/themes.xml` — Parchment background (`#F5EDD6`), amber accent (`#CC6600`) |
| **Dark Theme** | `res/values-night/themes.xml` — CRT black (`#131313`), neon green (`#00FF41`) |
| **Press Start 2P font** | Applied via `android:fontFamily="@font/press_start_2p"` on every TextView, EditText, and Button |
| **INTERNET permission** | `<uses-permission android:name="android.permission.INTERNET" />` in `AndroidManifest.xml` |
| **Error State** | `layout_error_state.xml` — "GAME OVER" screen with RETRY; shown when cache empty + no network |
| **API key in header** | Injected by OkHttp `addInterceptor` as `X-Api-Key`, never in URL query string |

---

## 🏛️ Architecture

```
┌──────────────────────────────────────────────────────────┐
│                      UI LAYER                            │
│  SplashActivity → MainActivity → NewsDetailActivity      │
│  HomeFeedFragment │ BookmarkFragment │ SettingsFragment  │
│  NewsAdapter (DiffUtil + Bookmark toggle)                │
└───────────────────────────┬──────────────────────────────┘
                            │ Callback (main thread via Handler)
┌───────────────────────────▼──────────────────────────────┐
│                  REPOSITORY LAYER                        │
│  NewsRepository — cache-first, Executor + Handler        │
│  ┌───────────────────┐     ┌──────────────────────────┐  │
│  │   NewsDbHelper    │     │       ApiClient          │  │
│  │ (SQLite WAL mode) │     │ (Retrofit + OkHttp 4)    │  │
│  │ INSERT OR REPLACE │     │ NewsAPI.org X-Api-Key    │  │
│  │ is_bookmarked col │     │ Timeout: 20s             │  │
│  │ Indexes: category │     │ /top-headlines           │  │
│  │          cached_at│     │ /everything              │  │
│  └───────────────────┘     └──────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### Threading Model

```
[Main Thread]   repo.getNews(callback)
                    │
[Executor]      SQLite read ──→ (if empty) Retrofit.execute() ──→ SQLite write
                    │
[Handler→Main]  callback.onSuccess(articles)  →  adapter.setArticles()
```

### Cache Strategy

```
getNews()            → SQLite first → API only if cache is empty
refreshNews()        → Force API   → overwrite SQLite    (Refresh button ONLY)
getNewsByCategory()  → SQLite only → no API call          (filter from cache)
bookmarkArticle()    → SQLite UPDATE is_bookmarked = 1
getBookmarkedNews()  → SQLite WHERE is_bookmarked = 1

Hard eviction : non-bookmarked entries older than 7 days deleted on cold start
Soft stale    : entries older than 24h are flagged (served, refresh optional)
Bookmarks     : never auto-evicted (always preserved across cache clears)
```

---

## 📦 Tech Stack

| Category | Library | Version |
|---|---|---|
| Language | Java | 11 |
| Min SDK | Android 11 | API 30 |
| Target SDK | Android 16 | API 36 |
| UI | AppCompat + Material3 | 1.7.1 / 1.13.0 |
| Navigation | AndroidX Navigation Component | 2.7.7 |
| Networking | Retrofit 2 + OkHttp 4 | 2.9.0 / 4.12.0 |
| JSON Parsing | Gson | 2.10.1 |
| Image Loading | Glide | 4.16.0 |
| Local Cache | SQLite via SQLiteOpenHelper | built-in |
| Preferences | SharedPreferences | built-in |
| Font | Press Start 2P (Google Fonts) | bundled in `res/font/` |

---

## 📁 Project Structure

```
app/src/main/
├── AndroidManifest.xml               # INTERNET permission, 3 activities declared
├── java/com/example/bit_stream_news/
│   ├── MainActivity.java             # NavHost + BottomNav + theme bootstrap
│   ├── NewsDetailActivity.java       # Article detail + share + open browser
│   ├── SplashActivity.java           # Animated 8-bit boot screen (2.5s)
│   ├── model/
│   │   ├── NewsArticle.java          # Local POJO with Builder pattern + bookmarked field
│   │   └── NewsApiResponse.java      # Gson-mapped NewsAPI.org response
│   ├── database/
│   │   └── NewsDbHelper.java         # SQLiteOpenHelper v2 (WAL + indexes + TTL + bookmarks)
│   ├── network/
│   │   ├── NewsApiService.java       # Retrofit interface (top-headlines, everything)
│   │   └── ApiClient.java            # OkHttp singleton + X-Api-Key interceptor
│   ├── repository/
│   │   └── NewsRepository.java       # Cache-first, Executor + Handler
│   └── ui/
│       ├── home/
│       │   ├── HomeFeedFragment.java # 4-state machine + search + chips + bookmark
│       │   └── NewsAdapter.java      # RecyclerView adapter + DiffUtil + Glide + bookmark toggle
│       ├── bookmark/
│       │   └── BookmarkFragment.java # Saved articles list + empty state + clear button
│       └── settings/
│           └── SettingsFragment.java # Dark mode toggle + cache purge
└── res/
    ├── layout/         (10 layout XMLs — all pixel art, zero rounded corners)
    ├── navigation/     nav_graph.xml (3 fragments, 6 actions, slide anims)
    ├── values/         colors.xml, themes.xml (light), styles.xml, strings.xml
    ├── values-night/   themes.xml (dark CRT green)
    ├── anim/           slide_in_right/left, slide_out_right/left (linear, 200ms)
    ├── drawable/       20+ pixel-art shape drawables + 3 vector nav icons
    ├── menu/           bottom_nav_menu.xml (3 items: FEED, SAVES, SET)
    ├── color/          bottom_nav_text_color.xml (state list)
    └── font/           press_start_2p.ttf
```

---

## 🚀 How to Run

### Prerequisites
- **Android Studio** Ladybug (2024.2.1) or newer
- **JDK 11** (bundled with Android Studio)
- Android device or emulator running **API 30+**

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/<your-username>/BitStreamNews.git
cd BitStreamNews

# 2. Open in Android Studio
#    File → Open → select the BitStreamNews folder

# 3. Sync Gradle
#    Android Studio auto-prompts: click "Sync Now"

# 4. Run the app
#    Select device/emulator → click ▶ Run (Shift+F10)
```

### First Launch Behaviour

```
Launch → Splash (2.5s boot animation)
       → Home Feed (cache empty on first launch)
       → Fetches from NewsAPI.org  ← 1 API request consumed
       → Saves to SQLite
       → All future launches: 100% offline from cache
       → Press [↺] only for fresh news  ← 1 more request
```

> ⚠️ **API Quota Notice**: The bundled NewsAPI.org key has a **100 req/day** free tier.  
> Normal daily use consumes ≤ 1 request/day. Do not call `refreshNews()` in a loop.

---

## 🌐 API Credit

| | |
|---|---|
| **Provider** | NewsAPI.org |
| **Base URL** | `https://newsapi.org/v2/` |
| **Endpoints used** | `GET /top-headlines`, `GET /everything` |
| **Docs** | https://newsapi.org/docs |
| **Auth** | `X-Api-Key` request header |
| **Free Tier** | 100 requests / day |

> The API key is injected via OkHttp `addInterceptor` header — never embedded in URL query parameters.

---

## 🎨 Design Reference

UI screens designed and exported from **Stitch** (Google DeepMind generative UI tool). 9 screens were exported and used as the visual specification for this implementation.

**Design System:**
- **Font**: Press Start 2P (SIL Open Font License)
- **Dark palette**: `#131313` CRT black · `#00FF41` neon green · `#FFE600` pixel yellow · `#FF2020` pixel red
- **Light palette**: `#F5EDD6` parchment · `#1A0A00` dark ink · `#CC6600` amber
- **Rules**: 0dp border radius · 2dp pixel stroke borders · 8dp grid · flat UI (no elevation)

---

## 👨‍💻 Author

| Field | Value |
|---|---|
| **Project** | BitStreamNews |
| **Course** | Praktikum Pemrograman Mobile |
| **Platform** | Android — Pure Java (no Kotlin / Coroutines) |
| **Year** | 2025 |

---

## 📄 License

This project is created for educational purposes as part of a university laboratory assignment.  
Press Start 2P font is licensed under the [SIL Open Font License 1.1](https://scripts.sil.org/OFL).

---

> *"INSERT COIN TO CONTINUE READING"*  
> `> PRESS_START_2_READ_`
