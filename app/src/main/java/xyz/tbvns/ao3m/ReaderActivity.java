package xyz.tbvns.ao3m;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import xyz.tbvns.ao3m.AO3.ChaptersAPI;
import xyz.tbvns.ao3m.Fragments.LoadingFragment;
import xyz.tbvns.ao3m.databinding.ActivityReaderBinding;

import java.util.List;

public class ReaderActivity extends AppCompatActivity {
    private static final boolean AUTO_HIDE = true;
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
    private static final int UI_ANIMATION_DELAY = 0;
    private final Handler mHideHandler = new Handler(Looper.myLooper());
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar
            if (Build.VERSION.SDK_INT >= 30) {
                mContentView.getWindowInsetsController().hide(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            } else {
                // Note that some of these constants are new as of API 16 (Jelly Bean)
                // and API 19 (KitKat). It is safe to use them, as they are inlined
                // at compile-time and do nothing on earlier devices.
                mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        }
    };
    private View mControlsView;
    private View mControlsViewTop;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            mControlsView.setVisibility(View.VISIBLE);
            mControlsViewTop.setVisibility(View.VISIBLE);

            animateContainer(mControlsView, false, false);
            animateContainer(mControlsViewTop, false, true);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (AUTO_HIDE) {
                        delayedHide(AUTO_HIDE_DELAY_MILLIS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    break;
                default:
                    break;
            }
            return false;
        }
    };
    private ActivityReaderBinding binding;

    public static String currentParagraphs;
    public static ChaptersAPI.Chapter currentChapter;

    public static void showFullscreen(FragmentManager manager, Context context, ChaptersAPI.Chapter chapter) {
        new Thread(() -> {
            new Handler((Looper.getMainLooper())).post(() -> {
                manager.beginTransaction()
                        .replace(R.id.fragment_container, new LoadingFragment())
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        .commit();
            });

            currentParagraphs = ChaptersAPI.fetchChapterParagraphs(chapter.getUrl());
            currentChapter = chapter;

            new Handler((Looper.getMainLooper())).post(() -> {
                Intent intent = new Intent(context, ReaderActivity.class);
                context.startActivity(intent);
            });
        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityReaderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mVisible = true;
        mControlsView = binding.fullscreenContentControls;
        mControlsViewTop = binding.fullscreenContentControlsTop;
        mContentView = binding.textDisplay;

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        setText(currentParagraphs);
        getSupportActionBar().hide();

        findViewById(R.id.backButton).setOnClickListener(l -> {
            finish();
        });
        ((TextView) findViewById(R.id.titleText)).setText(currentChapter.getWork().title);
        ((TextView) findViewById(R.id.chapterText)).setText(currentChapter.getTitle());

        ScrollView scrollView = findViewById(R.id.fullscreen_content);
        SeekBar seekBar = findViewById(R.id.progressBarChapter);

        scrollView.post(() -> {
            int maxScroll = scrollView.getChildAt(0).getHeight() - scrollView.getHeight();
            seekBar.setMax(maxScroll);
        });

        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            seekBar.setProgress(scrollY);
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    scrollView.scrollTo(0, progress);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    public void setText(String texts) {
        Document doc = Jsoup.parse(texts);

        Elements title = doc.select("div.chapter.preface.group h3.title");
        title.remove();

        Elements summary = doc.select("#summary.summary.module");
        String summaryString = summary.html();
        summary.remove();

        Elements startNotes = doc.select("#notes.notes.module");
        String startNotesString = startNotes.html();
        startNotes.remove();

        Elements endNotesElement = doc.select("div.end.notes.module");
        String endNotes = endNotesElement.html();
        endNotesElement.remove();

        LinearLayout layout = findViewById(R.id.textDisplay);

        if (!title.isEmpty()) {
            layout.addView(new TextView(getApplicationContext()){{
                setText(Html.fromHtml(title.html()));
                setPadding(0, 50, 0, 50);
                setTextSize(25);
            }});
        }

        if (!summaryString.isEmpty()) {
            TextView textView = new TextView(getApplicationContext()){{
                setText(Html.fromHtml(summaryString, Html.FROM_HTML_OPTION_USE_CSS_COLORS | Html.FROM_HTML_MODE_COMPACT));
                setPadding(50, 50, 50, 50);
                setTextSize(14);
            }};

            FrameLayout frameLayout = new FrameLayout(getApplicationContext());
            frameLayout.setPadding(50, 50, 50, 50); // Ensure outer padding
            frameLayout.addView(textView);
            frameLayout.setBackground(getDrawable(R.drawable.rounded_workview));

            layout.addView(frameLayout);

            Space space = new Space(getApplicationContext());
            space.setMinimumHeight(25);
            layout.addView(space);
        }

        if (!startNotesString.isEmpty()) {
            TextView textView = new TextView(getApplicationContext()){{
                setText(Html.fromHtml(startNotesString, Html.FROM_HTML_OPTION_USE_CSS_COLORS | Html.FROM_HTML_MODE_COMPACT));
                setPadding(50, 50, 50, 50);
                setTextSize(14);
            }};

            // Wrap in a container to ensure padding is respected
            FrameLayout frameLayout = new FrameLayout(getApplicationContext());
            frameLayout.setPadding(50, 50, 50, 50); // Ensure outer padding
            frameLayout.addView(textView);
            frameLayout.setBackground(getDrawable(R.drawable.rounded_workview));

            layout.addView(frameLayout);

            Space space = new Space(getApplicationContext());
            space.setMinimumHeight(25);
            layout.addView(space);
        }

        layout.addView(new TextView(getApplicationContext()){{
            setText(Html.fromHtml(doc.html(), Html.FROM_HTML_OPTION_USE_CSS_COLORS | Html.FROM_HTML_MODE_LEGACY));
            setPadding(0, 50, 0, 50);
            setTextSize(16);
        }});

        if (!endNotes.isEmpty()) {
            TextView textView = new TextView(getApplicationContext()) {{
                setText(Html.fromHtml(endNotes, Html.FROM_HTML_OPTION_USE_CSS_COLORS | Html.FROM_HTML_MODE_COMPACT));
                setPadding(50, 50, 50, 50);
                setTextSize(14);
            }};

            // Wrap in a container to ensure padding is respected
            FrameLayout frameLayout = new FrameLayout(getApplicationContext());
            frameLayout.setPadding(50, 50, 50, 50); // Ensure outer padding
            frameLayout.addView(textView);
            frameLayout.setBackground(getDrawable(R.drawable.rounded_workview));

            layout.addView(frameLayout);
        }

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        animateContainer(mControlsView, true, false);
        animateContainer(mControlsViewTop, true, true);

//        mControlsView.setVisibility(View.GONE);
//        mControlsViewTop.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void show() {
        mVisible = true;

        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    private void animateContainer(View view, boolean isGoingOut, boolean isAtTop) {
        float translationY = isGoingOut ? (isAtTop ? -view.getHeight() : view.getHeight()) : 0;
        int visibility = isGoingOut ? View.GONE : View.VISIBLE;

        view.animate()
                .translationY(translationY)
                .setDuration(250)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (animation != null) {
                            animation.removeAllListeners();
                        }
                        view.clearAnimation();
                        view.setVisibility(visibility);
                    }
                });
    }
}