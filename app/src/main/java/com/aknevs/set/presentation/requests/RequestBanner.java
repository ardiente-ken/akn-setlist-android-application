package com.aknevs.set.presentation.requests;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.aknevs.set.R;
import com.aknevs.set.domain.model.SongRequest;
import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.usecase.FindRequestedSongUseCase;
import com.aknevs.set.presentation.songdetail.SongDetailActivity;

import java.util.ArrayList;

/**
 * A banner pinned to the TOP of the screen announcing a new request.
 *  - Tap: opens the chords of the requested song (or the queue, if it is not in the library).
 *  - Swipe up, left or right: dismisses it.
 * It slides in, stays for a few seconds, and slides away. A newer request replaces the text.
 */
public final class RequestBanner {

    private static final long VISIBLE_MS = 7000L;
    private static final long ANIM_MS = 220L;

    private final AppCompatActivity activity;
    private final FindRequestedSongUseCase findSong;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoHide = this::hide;
    private final float hiddenOffset;
    private final int touchSlop;

    private View banner;
    private TextView titleView;
    private TextView artistView;
    private SongRequest current;
    private boolean showing;

    public RequestBanner(AppCompatActivity activity, FindRequestedSongUseCase findSong) {
        this.activity = activity;
        this.findSong = findSong;
        this.hiddenOffset = 160 * activity.getResources().getDisplayMetrics().density;
        this.touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
    }

    public void show(SongRequest request) {
        ensureView();
        current = request;
        titleView.setText(request.getTitle());
        boolean hasArtist = !request.getArtist().trim().isEmpty();
        artistView.setVisibility(hasArtist ? View.VISIBLE : View.GONE);
        artistView.setText(request.getArtist());

        handler.removeCallbacks(autoHide);
        if (!showing) {
            showing = true;
            banner.animate().cancel();
            banner.setVisibility(View.VISIBLE);
            banner.setAlpha(1f);
            banner.setTranslationX(0f);
            banner.setTranslationY(-hiddenOffset);
            banner.animate().translationY(0f).setDuration(ANIM_MS).start();
        }
        handler.postDelayed(autoHide, VISIBLE_MS);
    }

    /** Call when the screen is destroyed. */
    public void release() {
        handler.removeCallbacksAndMessages(null);
        if (banner != null && banner.getParent() instanceof ViewGroup) {
            banner.animate().cancel();
            ((ViewGroup) banner.getParent()).removeView(banner);
        }
        banner = null;
        showing = false;
    }

    // ---------- showing / hiding ----------

    private void hide() {
        slideAway(0f, -hiddenOffset);
    }

    /** Animates the banner out to (toX, toY), then hides it and resets it for next time. */
    private void slideAway(float toX, float toY) {
        if (!showing || banner == null) return;
        showing = false;
        handler.removeCallbacks(autoHide);
        banner.animate().translationX(toX).translationY(toY).alpha(0f).setDuration(ANIM_MS)
                .withEndAction(() -> {
                    if (!showing && banner != null) {
                        banner.setVisibility(View.GONE);
                        banner.setAlpha(1f);
                        banner.setTranslationX(0f);
                    }
                }).start();
    }

    private void snapBack() {
        if (banner == null) return;
        banner.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(150L).start();
        handler.removeCallbacks(autoHide);
        handler.postDelayed(autoHide, VISIBLE_MS);
    }

    private void ensureView() {
        if (banner != null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        banner = LayoutInflater.from(activity).inflate(R.layout.banner_request, content, false);
        titleView = banner.findViewById(R.id.text_banner_title);
        artistView = banner.findViewById(R.id.text_banner_artist);
        banner.setOnClickListener(v -> {
            hide();
            if (current != null) openChords(current);
        });
        banner.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        dragging = false;
                        handler.removeCallbacks(autoHide); // stay put while being touched
                        return true;

                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            dragging = true;
                        }
                        if (dragging) {
                            if (Math.abs(dx) > Math.abs(dy)) { // sideways
                                v.setTranslationX(dx);
                                v.setTranslationY(0f);
                                v.setAlpha(1f - Math.min(1f, Math.abs(dx) / v.getWidth()));
                            } else {                           // upwards only
                                v.setTranslationX(0f);
                                v.setTranslationY(Math.min(0f, dy));
                                v.setAlpha(1f);
                            }
                        }
                        return true;
                    }

                    case MotionEvent.ACTION_UP: {
                        if (!dragging) {
                            v.performClick(); // a tap: open the chords
                            return true;
                        }
                        float dx = v.getTranslationX();
                        float dy = v.getTranslationY();
                        if (Math.abs(dx) > v.getWidth() * 0.3f) {
                            slideAway(Math.signum(dx) * v.getWidth(), 0f);
                        } else if (-dy > v.getHeight() * 0.4f) {
                            slideAway(0f, -hiddenOffset);
                        } else {
                            snapBack();
                        }
                        return true;
                    }

                    case MotionEvent.ACTION_CANCEL:
                        snapBack();
                        return true;

                    default:
                        return false;
                }
            }
        });
        content.addView(banner); // added last, so it draws above the screen
    }

    // ---------- opening ----------

    private void openChords(SongRequest request) {
        findSong.execute(request.getSongId(), request.getTitle(), request.getArtist(),
                new RepositoryCallback<String>() {
                    @Override
                    public void onSuccess(String songId) {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        if (songId == null) {
                            openQueue(); // not in the library: the queue explains what to do
                            return;
                        }
                        ArrayList<String> ids = new ArrayList<>();
                        ids.add(songId);
                        activity.startActivity(SongDetailActivity.newIntent(activity, ids, 0));
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!activity.isFinishing() && !activity.isDestroyed()) openQueue();
                    }
                });
    }

    private void openQueue() {
        RequestQueueSheet.show(activity.getSupportFragmentManager());
    }
}
