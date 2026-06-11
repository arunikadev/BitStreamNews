package com.example.bit_stream_news;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * SplashActivity — 8-bit boot screen.
 *
 * Shows the pixel logo, animates the loading progress bar from 0→100%,
 * cycles terminal status lines, then launches MainActivity after the
 * animation completes (~2.5 s total). Finishes itself so the user
 * cannot navigate back to the splash with the Back button.
 *
 * Uses android:noHistory="true" in the Manifest as a belt-and-suspenders
 * guard against back-stack issues.
 */
public class SplashActivity extends AppCompatActivity {

    // ── Timing constants (ms) ─────────────────────────────────────────────────

    /** Total animation duration before launching MainActivity. */
    private static final int TOTAL_DURATION_MS = 2500;

    /** Delay between terminal status line reveals. */
    private static final int STATUS_LINE_INTERVAL_MS = 600;

    // ── Views ─────────────────────────────────────────────────────────────────

    private ProgressBar progressBar;
    private TextView    tvStatusLine1;
    private TextView    tvStatusLine2;
    private TextView    tvStatusLine3;
    private View        cursorBlock;

    // ── State ─────────────────────────────────────────────────────────────────

    private final Handler       handler      = new Handler(Looper.getMainLooper());
    private       ObjectAnimator progressAnim;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        bindViews();
        startBootSequence();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel pending runnables to prevent leaks after screen rotation/kill
        handler.removeCallbacksAndMessages(null);
        if (progressAnim != null) progressAnim.cancel();
    }

    // ── Boot sequence ─────────────────────────────────────────────────────────

    private void bindViews() {
        progressBar   = findViewById(R.id.progress_bar_splash);
        tvStatusLine1 = findViewById(R.id.tv_status_line_1);
        tvStatusLine2 = findViewById(R.id.tv_status_line_2);
        tvStatusLine3 = findViewById(R.id.tv_status_line_3);
        cursorBlock   = findViewById(R.id.cursor_block);
    }

    private void startBootSequence() {
        // 1. Animate progress bar 0→100 over TOTAL_DURATION_MS
        animateProgressBar();

        // 2. Blink the cursor block
        startCursorBlink();

        // 3. Reveal terminal status lines at staggered intervals
        // Line 1 is already visible (set in XML)
        handler.postDelayed(this::showStatusLine2,     STATUS_LINE_INTERVAL_MS);
        handler.postDelayed(this::showStatusLine3,     STATUS_LINE_INTERVAL_MS * 2);

        // 4. Launch MainActivity after total duration
        handler.postDelayed(this::launchMainActivity,  TOTAL_DURATION_MS);
    }

    private void animateProgressBar() {
        progressAnim = ObjectAnimator.ofInt(progressBar, "progress", 0, 100);
        progressAnim.setDuration(TOTAL_DURATION_MS - 300); // finish slightly before launch
        progressAnim.setInterpolator(new LinearInterpolator());
        progressAnim.addUpdateListener(animator -> {
            int value = (int) animator.getAnimatedValue();
            // Update the percentage TextView next to the bar
            TextView tvPercent = findViewById(R.id.tv_progress_percent);
            if (tvPercent != null) tvPercent.setText(value + "%");
        });
        progressAnim.start();
    }

    private void startCursorBlink() {
        if (cursorBlock == null) return;
        ValueAnimator blink = ValueAnimator.ofFloat(1f, 0f, 1f);
        blink.setDuration(800);
        blink.setRepeatCount(ValueAnimator.INFINITE);
        blink.setInterpolator(new LinearInterpolator());
        blink.addUpdateListener(a -> {
            if (cursorBlock != null) cursorBlock.setAlpha((Float) a.getAnimatedValue());
        });
        blink.start();
    }

    private void showStatusLine2() {
        if (tvStatusLine2 != null) {
            tvStatusLine2.setVisibility(View.VISIBLE);
        }
    }

    private void showStatusLine3() {
        if (tvStatusLine3 != null) {
            tvStatusLine3.setVisibility(View.VISIBLE);
        }
    }

    private void launchMainActivity() {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        // Clear task so MainActivity is fresh root
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        // No transition animation — instant pixel "screen switch"
        overridePendingTransition(0, 0);
        finish();
    }
}
