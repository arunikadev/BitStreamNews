# BitStreamNews — Antigravity Prompt & Konteks Proyek

---

## BAGIAN 1: KONTEKS PROYEK

### Deskripsi Aplikasi
**BitStreamNews** adalah aplikasi Android bertema **Berita & Informasi** dengan visual **Retro 8-bit / Pixel Art**.
Aplikasi ini dibangun sebagai Tugas Final Lab Mobile 2026, mengimplementasikan seluruh spesifikasi teknis yang diwajibkan.

### Stack Teknis
| Komponen | Detail |
|---|---|
| Platform | Android (Java/Kotlin) |
| Min SDK | (sesuai project) |
| Font | `press_start_2p.ttf` (sudah ada di `res/font/`) |
| Networking | Retrofit + RapidAPI |
| Local Storage | SQLite |
| Theme | Light & Dark (CRT / Parchment) |
| Navigation | Navigation Component (NavGraph) |

### API
- **Provider:** RapidAPI
- **Header:** `x-rapidapi-key: c83a05807bmsh4eb712db1696341p185a14jsnbead526a1a8d`
- **Kuota:** 100 request/bulan → **wajib cache semua response ke SQLite**
- **Strategi hemat request:**
  - Simpan semua data berita ke database lokal setelah pertama kali fetch
  - Hanya fetch ulang bila user menekan tombol **Refresh** secara eksplisit
  - Tampilkan data lokal secara default saat aplikasi dibuka

---

## BAGIAN 2: STRUKTUR APLIKASI

### Activity (Minimal 2)
```
MainActivity        → Launcher, host Fragment utama (Home, Category, Settings)
NewsDetailActivity  → Menampilkan detail berita, menerima data via Intent
```

### Fragment (Minimal 2) + Navigation Component
```
HomeFeedFragment      → Daftar berita utama (RecyclerView)
CategoryFragment      → Browse berita per kategori (grid level-select style)
SettingsFragment      → Pengaturan tema Light/Dark, preferensi
```

### Navigation Graph (`res/navigation/nav_graph.xml`)
```
HomeFeedFragment ──→ CategoryFragment
HomeFeedFragment ──→ SettingsFragment
CategoryFragment ──→ HomeFeedFragment
(antar Fragment via Navigation Component)
MainActivity ──Intent──→ NewsDetailActivity
```

### RecyclerView
- Digunakan di `HomeFeedFragment` untuk daftar berita
- Digunakan di `CategoryFragment` untuk grid kategori
- Adapter custom dengan ViewHolder, pixel-styled card layout

---

## BAGIAN 3: SPESIFIKASI TEKNIS WAJIB

### 3.1 Background Thread (Executor + Handler)
```java
// Pola yang harus digunakan:
ExecutorService executor = Executors.newSingleThreadExecutor();
Handler handler = new Handler(Looper.getMainLooper());

executor.execute(() -> {
    // Operasi berat: fetch API, query SQLite
    List<News> result = fetchFromDatabase();
    handler.post(() -> {
        // Update UI di main thread
        adapter.setData(result);
    });
});
```
- Semua operasi Retrofit dan SQLite **wajib** di background thread
- Update UI selalu melalui Handler ke main thread

### 3.2 Networking — Retrofit
```java
// Interface API
public interface NewsApiService {
    @GET("endpoint")
    Call<NewsResponse> getTopHeadlines(
        @Query("category") String category,
        @Query("language") String language
    );
}

// Header RapidAPI
OkHttpClient client = new OkHttpClient.Builder()
    .addInterceptor(chain -> chain.proceed(
        chain.request().newBuilder()
            .addHeader("x-rapidapi-key", "c83a05807bmsh4eb712db1696341p185a14jsnbead526a1a8d")
            .addHeader("x-rapidapi-host", "[host-sesuai-api]")
            .build()
    )).build();
```

### 3.3 Tombol Refresh (Wajib)
- Muncul di state **Error / No Internet** (layar "GAME OVER")
- Styled sebagai tombol pixel **"RETRY"**
- Memicu ulang fetch dari API

### 3.4 Local Data Persistence — SQLite
```sql
-- Tabel utama
CREATE TABLE news (
    id TEXT PRIMARY KEY,
    title TEXT,
    description TEXT,
    url TEXT,
    image_url TEXT,
    published_at TEXT,
    category TEXT,
    source TEXT,
    cached_at INTEGER  -- timestamp untuk invalidasi cache
);
```
- Data harus tampil **offline** dari SQLite bila tidak ada koneksi
- Cache diperbarui hanya saat fetch API berhasil

### 3.5 Light & Dark Theme
```xml
<!-- res/values/themes.xml → Light (Parchment CRT) -->
<!-- res/values/night/themes.xml → Dark (Classic CRT) -->
```
- Toggle via `SettingsFragment`
- Disimpan di **SharedPreferences** (`theme_mode`)
- Diterapkan di `MainActivity.onCreate()` sebelum `setContentView`

---

## BAGIAN 4: DESAIN STITCH — REFERENSI SCREEN

### Project Stitch
- **Title:** 8-Bit News Hub
- **Project ID:** `13280051740303879868`

### Daftar Screen
| No | Nama Screen | ID |
|---|---|---|
| 1 | Design System | `asset-stub-assets_f1c64cee166e4283bc3b70863a8f6867` |
| 2 | Home Feed | `c9309ead085649f48397a9f453137307` |
| 3 | News Detail | `13a153c0ea2a4bbea8c97fb86d5e03b0` |
| 4 | Splash Screen | `b49794f6b87142c2944d95d0ebb5e82e` |
| 5 | Category Browse | `c4af7b2422dc4c81b625b0a6f027de8d` |
| 6 | Settings Screen | `97daecdcb4634996ae69d820cac5c707` |
| 7 | Error State (Game Over) | `60a49cd514a44187925d4c01deaba1c3` |
| 8 | Splash Screen (Animated) | `02f47b856c894d25b8bb4072a98a6ae6` |
| 9 | Home Feed (Animated) | `4baa23567c3848fbbbe7bb44b4451306` |
| 10 | Error State (Animated) | `222f2278f0b540ee8999946666ff949c` |

---



## BAGIAN 5: CATATAN PENTING

### Hemat API Request
> Karena kuota hanya **100 req/bulan**, implementasi cache **sangat kritis**.
> Jangan pernah auto-refresh saat app dibuka. Selalu cek SQLite dulu.

### File yang Sudah Ada (Jangan Di-overwrite)
- `res/font/press_start_2p.ttf` ✅
- Semua Gradle config & dependencies ✅

### Urutan Build yang Disarankan
1. Theme & Colors → 2. Layouts → 3. Navigation → 4. Data Layer → 5. UI Logic → 6. Testing

### Deadline
| Event | Tanggal |
|---|---|
| Pengumpulan APK + GitHub | **12 Juni 2026** |
| Presentasi | **17 Juni 2026** |