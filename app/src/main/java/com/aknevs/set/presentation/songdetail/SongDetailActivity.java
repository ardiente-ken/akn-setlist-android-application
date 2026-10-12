package com.aknevs.set.presentation.songdetail;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.aknevs.set.AknApplication;
import com.aknevs.set.R;
import com.aknevs.set.domain.model.Song;
import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.usecase.GetSongUseCase;

import java.util.ArrayList;

/**
 * Read-only performance view. Receives the ordered list of song ids it can move through
 * (Prev/Next/swipe) and always loads the song itself from local storage.
 */
public class SongDetailActivity extends AppCompatActivity {

    private static final String EXTRA_SONG_IDS = "com.aknevs.set.SONG_IDS";
    private static final String EXTRA_INDEX = "com.aknevs.set.INDEX";
    private static final String STATE_INDEX = "state_index";
    private static final String STATE_CHORDS = "state_chords";

    private static final String PREFS = "akn_view_prefs";
    private static final String KEY_FONT_SP = "detail_font_sp";
    private static final int MIN_SP = 12;
    private static final int MAX_SP = 32;
    private static final int STEP_SP = 2;
    private static final int DEFAULT_SP = 16;

    public static Intent newIntent(Context context, ArrayList<String> songIds, int index) {
        return new Intent(context, SongDetailActivity.class)
                .putStringArrayListExtra(EXTRA_SONG_IDS, songIds)
                .putExtra(EXTRA_INDEX, index);
    }

    private GetSongUseCase getSong;
    private SharedPreferences prefs;
    private GestureDetector gestures;

    private ArrayList<String> ids;
    private int index;
    private int fontSp;
    private boolean showChords = true;
    private Song currentSong;
    private int requestId = 0;
    private int lastChartWidth = -1;

    private ScrollView scroll;
    private View content;
    private TextView positionText;
    private TextView titleText;
    private TextView artistText;
    private TextView chordsSummary;
    private TextView chart;
    private TextView smallerButton;
    private TextView largerButton;
    private TextView chordsButton;
    private TextView prevButton;
    private TextView nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_detail);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // stage-safe

        ids = getIntent().getStringArrayListExtra(EXTRA_SONG_IDS);
        if (ids == null || ids.isEmpty()) {
            finish();
            return;
        }
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        if (savedInstanceState != null) {
            index = savedInstanceState.getInt(STATE_INDEX, index);
            showChords = savedInstanceState.getBoolean(STATE_CHORDS, true);
        }
        index = Math.max(0, Math.min(index, ids.size() - 1));

        getSong = ((AknApplication) getApplication()).getGetSongUseCase();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        fontSp = Math.max(MIN_SP, Math.min(MAX_SP, prefs.getInt(KEY_FONT_SP, DEFAULT_SP)));

        scroll = findViewById(R.id.scroll_song);
        content = findViewById(R.id.content_song);
        positionText = findViewById(R.id.text_position);
        titleText = findViewById(R.id.text_song_title);
        artistText = findViewById(R.id.text_song_artist);
        chordsSummary = findViewById(R.id.text_chords_summary);
        chart = findViewById(R.id.text_chart);
        smallerButton = findViewById(R.id.button_smaller);
        largerButton = findViewById(R.id.button_larger);
        chordsButton = findViewById(R.id.button_chords);
        prevButton = findViewById(R.id.button_prev);
        nextButton = findViewById(R.id.button_next);

        findViewById(R.id.button_back).setOnClickListener(v -> finish());
        smallerButton.setOnClickListener(v -> changeFont(-STEP_SP));
        largerButton.setOnClickListener(v -> changeFont(STEP_SP));
        chordsButton.setOnClickListener(v -> toggleChords());
        prevButton.setOnClickListener(v -> go(-1));
        nextButton.setOnClickListener(v -> go(1));

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                float minDistance = 80 * getResources().getDisplayMetrics().density;
                // Clearly horizontal only, so vertical scrolling never changes the song.
                if (Math.abs(dx) > minDistance && Math.abs(dx) > Math.abs(dy) * 2
                        && Math.abs(vx) > 800) {
                    go(dx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });

        // Re-flow the chart whenever its width changes (rotation, split screen).
        chart.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            int width = r - l;
            if (width != lastChartWidth) {
                lastChartWidth = width;
                renderChart();
            }
        });

        chordsButton.setSelected(showChords);
        applyFontSize();
        loadSong();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_INDEX, index);
        outState.putBoolean(STATE_CHORDS, showChords);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures != null) gestures.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    // ---------- navigation ----------

    private void go(int offset) {
        int target = index + offset;
        if (target < 0 || target >= ids.size()) return;
        index = target;
        loadSong();
    }

    private void loadSong() {
        updateNavigation();
        final int myRequest = ++requestId;
        getSong.execute(ids.get(index), new RepositoryCallback<Song>() {
            @Override
            public void onSuccess(Song song) {
                if (isFinishing() || isDestroyed() || myRequest != requestId) return;
                display(song);
            }

            @Override
            public void onError(Exception error) {
                if (isFinishing() || isDestroyed() || myRequest != requestId) return;
                currentSong = null;
                titleText.setText(R.string.song_not_found);
                artistText.setText("");
                chordsSummary.setVisibility(View.GONE);
                chart.setText("");
            }
        });
    }

    private void updateNavigation() {
        positionText.setText(getString(R.string.detail_position, index + 1, ids.size()));
        setEnabledLook(prevButton, index > 0);
        setEnabledLook(nextButton, index < ids.size() - 1);
    }

    private static void setEnabledLook(TextView button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.35f);
    }

    // ---------- display ----------

    private void display(Song song) {
        currentSong = song;
        titleText.setText(song.getTitle());
        artistText.setText(song.getArtist());
        updateChordsSummary();
        renderChart();
        scroll.scrollTo(0, 0);
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(140).start(); // quiet fade between songs
    }

    private void updateChordsSummary() {
        String chords = currentSong == null ? "" : currentSong.getChords();
        boolean visible = showChords && chords != null && !chords.trim().isEmpty();
        chordsSummary.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) chordsSummary.setText(chords);
    }

    private void renderChart() {
        if (currentSong == null) return;
        int width = chart.getWidth() - chart.getPaddingLeft() - chart.getPaddingRight();
        if (width <= 0) return; // not laid out yet; the layout listener will call again
        float charWidth = chart.getPaint().measureText("0");
        int cols = Math.max(8, (int) (width / charWidth));
        chart.setText(ChordChartRenderer.render(
                currentSong.getLyrics(), showChords, cols,
                ContextCompat.getColor(this, R.color.akn_accent),
                ContextCompat.getColor(this, R.color.akn_text_primary),
                ContextCompat.getColor(this, R.color.akn_text_secondary)));
    }

    // ---------- tools ----------

    private void changeFont(int delta) {
        int updated = Math.max(MIN_SP, Math.min(MAX_SP, fontSp + delta));
        if (updated == fontSp) return;
        fontSp = updated;
        prefs.edit().putInt(KEY_FONT_SP, fontSp).apply();
        applyFontSize();
        renderChart();
    }

    private void applyFontSize() {
        chart.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp);
        chordsSummary.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp);
        setEnabledLook(smallerButton, fontSp > MIN_SP);
        setEnabledLook(largerButton, fontSp < MAX_SP);
    }

    private void toggleChords() {
        showChords = !showChords;
        chordsButton.setSelected(showChords);
        updateChordsSummary();
        renderChart();
    }
}
