package com.example.bit_stream_news.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.bit_stream_news.MainActivity;
import com.example.bit_stream_news.R;
import com.example.bit_stream_news.database.NewsDbHelper;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SettingsFragment — BIOS-style settings screen.
 *
 * Controls:
 *
 *   DARK_MODE toggle (SwitchMaterial)
 *   ─────────────────────────────────
 *   Reads initial state from SharedPreferences ("app_prefs" → "theme_mode").
 *   On toggle: writes new value → calls AppCompatDelegate.setDefaultNightMode()
 *   → calls requireActivity().recreate() so MainActivity picks up the theme.
 *   Note: recreate() causes the full Activity + all Fragments to be re-created.
 *   The theme is re-applied by MainActivity.applyThemeFromPrefs() before
 *   setContentView(), ensuring no FOUC (flash of un-themed content).
 *
 *   NOTIFICATIONS toggle (SwitchMaterial)
 *   ──────────────────────────────────────
 *   Persists a boolean pref only (no system permission requested here —
 *   the lab spec doesn't require runtime POST_NOTIFICATIONS handling).
 *
 *   PURGE CACHE button
 *   ───────────────────
 *   Calls NewsDbHelper.clearAllCache() on a background Executor thread.
 *   Shows a Toast on success (posted back to main thread via view.post()).
 *
 *   COMMIT / DISCARD buttons
 *   ─────────────────────────
 *   COMMIT: saves any pending prefs and calls recreate() if theme changed.
 *   DISCARD: resets toggles to saved state without saving.
 */
public class SettingsFragment extends Fragment {

    // ── Prefs constants (mirror MainActivity) ─────────────────────────────────
    private static final String PREFS_NAME         = MainActivity.PREFS_NAME;
    private static final String KEY_THEME          = MainActivity.KEY_THEME;
    private static final String KEY_NOTIFICATIONS  = "notifications_enabled";
    private static final String THEME_DARK         = MainActivity.THEME_DARK;
    private static final String THEME_LIGHT        = MainActivity.THEME_LIGHT;

    // ── Views ─────────────────────────────────────────────────────────────────

    private SwitchMaterial switchDarkMode;
    private SwitchMaterial switchNotifications;
    private View           btnClearCache;
    private View           btnSaveSettings;
    private View           btnDiscardSettings;

    // ── State ─────────────────────────────────────────────────────────────────

    /** True if the user toggled the theme switch since last save. */
    private boolean themeChanged = false;

    /** Theme value at the time the Fragment was created (for discard logic). */
    private String savedTheme;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "settings-executor");
                t.setDaemon(true);
                return t;
            });

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        loadPrefsIntoUI();
        setupListeners();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        switchDarkMode      = null;
        switchNotifications = null;
        btnClearCache       = null;
        btnSaveSettings     = null;
        btnDiscardSettings  = null;
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void bindViews(View root) {
        switchDarkMode      = root.findViewById(R.id.switch_dark_mode);
        switchNotifications = root.findViewById(R.id.switch_notifications);
        btnClearCache       = root.findViewById(R.id.btn_clear_cache);
        btnSaveSettings     = root.findViewById(R.id.btn_save_settings);
        btnDiscardSettings  = root.findViewById(R.id.btn_discard_settings);
    }

    /**
     * Reads current prefs and sets switch states without triggering listeners.
     */
    private void loadPrefsIntoUI() {
        SharedPreferences prefs = requireActivity()
                .getSharedPreferences(PREFS_NAME, 0);

        savedTheme = prefs.getString(KEY_THEME, THEME_LIGHT);
        boolean isDark = THEME_DARK.equals(savedTheme);

        if (switchDarkMode      != null) switchDarkMode.setChecked(isDark);
        if (switchNotifications != null) switchNotifications.setChecked(
                prefs.getBoolean(KEY_NOTIFICATIONS, false));
    }

    private void setupListeners() {
        // DARK MODE toggle
        if (switchDarkMode != null) {
            switchDarkMode.setOnCheckedChangeListener((btn, isChecked) -> {
                themeChanged = true;
                // Live preview: apply immediately (Activity recreates on COMMIT)
                applyNightMode(isChecked);
            });
        }

        // NOTIFICATIONS toggle — just persist, no system permission needed here
        if (switchNotifications != null) {
            switchNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
                requireActivity()
                        .getSharedPreferences(PREFS_NAME, 0)
                        .edit()
                        .putBoolean(KEY_NOTIFICATIONS, isChecked)
                        .apply();
            });
        }

        // PURGE CACHE button
        if (btnClearCache != null) {
            btnClearCache.setOnClickListener(v -> clearCache());
        }

        // COMMIT button
        if (btnSaveSettings != null) {
            btnSaveSettings.setOnClickListener(v -> commitSettings());
        }

        // DISCARD button
        if (btnDiscardSettings != null) {
            btnDiscardSettings.setOnClickListener(v -> discardSettings());
        }
    }

    // ── Theme switching ───────────────────────────────────────────────────────

    /**
     * Applies night mode immediately and saves the preference.
     * Does NOT recreate the Activity here — that happens on COMMIT to avoid
     * jarring mid-edit recreation.
     */
    private void applyNightMode(boolean isDark) {
        String theme = isDark ? THEME_DARK : THEME_LIGHT;

        // Persist immediately so if the user kills the app the pref is saved
        requireActivity()
                .getSharedPreferences(PREFS_NAME, 0)
                .edit()
                .putString(KEY_THEME, theme)
                .apply();

        int mode = isDark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    // ── Commit / Discard ──────────────────────────────────────────────────────

    /**
     * Saves all settings and recreates the Activity so the new theme
     * is fully applied from scratch (required by Material3).
     */
    private void commitSettings() {
        // Theme already saved in applyNightMode() when switch was toggled.
        // Recreate to flush all theme caches.
        if (themeChanged) {
            requireActivity().recreate();
        } else {
            // No theme change — just show confirmation
            showToast(getString(R.string.settings_cache_cleared)
                    .replace("CACHE_PURGED: MEMORY_FREED", "SETTINGS_SAVED"));
        }
    }

    /**
     * Resets switches to the saved state without persisting any changes.
     */
    private void discardSettings() {
        themeChanged = false;
        // Restore to last saved theme
        boolean wasDark = THEME_DARK.equals(savedTheme);
        if (switchDarkMode != null) {
            // Temporarily remove listener to avoid triggering applyNightMode
            switchDarkMode.setOnCheckedChangeListener(null);
            switchDarkMode.setChecked(wasDark);
            // Re-attach listener
            switchDarkMode.setOnCheckedChangeListener((btn, isChecked) -> {
                themeChanged = true;
                applyNightMode(isChecked);
            });
        }
        // Revert night mode to saved state
        applyNightMode(wasDark);
        showToast("CHANGES_DISCARDED");
    }

    // ── Cache purge ───────────────────────────────────────────────────────────

    /**
     * Clears the entire SQLite news cache on a background thread.
     * Posts a Toast back to the main thread on completion.
     */
    private void clearCache() {
        View rootView = getView();
        executor.execute(() -> {
            NewsDbHelper.getInstance(requireContext()).clearAllCache();
            if (rootView != null) {
                rootView.post(() -> showToast(
                        getString(R.string.settings_cache_cleared)));
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
