package com.hezi.flix;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public class PlayerActivity extends BaseActivity {

    private final String API_KEY = "049e000306b8490d040be01ddda7abf4";

    private ImageView ivBackdrop;
    private TextView tvTitle, tvOverview, tvRatingStars, tvYear;
    private Button btnWatchNow, btnWatchLater; 
    private ListView lvSuggestions;
    private SuggestionAdapter suggestionAdapter;
    private ArrayList<MovieSuggestion> suggestionsList = new ArrayList<MovieSuggestion>();

    private int currentSuggestionsPage = 1;
    private int totalSuggestionsPages = 1;
    private boolean isSuggestionsLoading = false;

    private WebView playerWebView;
    private ProgressBar progressBar;
    private LinearLayout selectorContainer;
    private Spinner spinnerSeason;
    private Spinner spinnerEpisode;
    private Button btnToggleSelectors;

    private String contentId;
    private boolean isTvShow;

    private String savedRawPosterPath = "";
    private double savedRatingNumericValue = 0.0;

    private ArrayList<Integer> seasonList = new ArrayList<Integer>();
    private ArrayList<Integer> episodeList = new ArrayList<Integer>();
    private int selectedSeasonNum = 1;
    private int selectedEpisodeNum = 1;
    private JSONArray seasonsJsonArray = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        contentId = getIntent().getStringExtra("MOVIE_ID");
        isTvShow = getIntent().getBooleanExtra("IS_TV", false);

        loadOverviewDetailsScreen();
    }

    private void loadOverviewDetailsScreen() {
        setContentView(R.layout.activity_info_panel);

        ivBackdrop = (ImageView) findViewById(R.id.detail_backdrop);
        tvTitle = (TextView) findViewById(R.id.detail_title);
        tvOverview = (TextView) findViewById(R.id.detail_overview);
        tvRatingStars = (TextView) findViewById(R.id.detail_rating_stars);
        tvYear = (TextView) findViewById(R.id.detail_year);
        btnWatchNow = (Button) findViewById(R.id.btn_watch_now);
        btnWatchLater = (Button) findViewById(R.id.btn_add_list); 
        lvSuggestions = (ListView) findViewById(R.id.lv_suggestions);

        currentSuggestionsPage = 1;
        totalSuggestionsPages = 1;
        suggestionsList.clear();
        suggestionAdapter = new SuggestionAdapter(this, suggestionsList);
        if (lvSuggestions != null) {
            lvSuggestions.setAdapter(suggestionAdapter);
        }

        updateWatchLaterButtonUI();

        String typePath = isTvShow ? "tv" : "movie";
        String detailsUrl = "https://api.themoviedb.org/3/" + typePath + "/" + contentId + "?api_key=" + API_KEY;
        new FetchDetailsTask().execute(detailsUrl);

        btnWatchNow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadVideoStreamingScreen();
                }
            });

        if (btnWatchLater != null) {
            btnWatchLater.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        executeWatchLaterToggleAction();
                    }
                });
        }

        if (lvSuggestions != null) {
            lvSuggestions.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        MovieSuggestion tapped = suggestionsList.get(position);
                        contentId = tapped.getId();
                        loadOverviewDetailsScreen();
                    }
                });

            lvSuggestions.setOnScrollListener(new AbsListView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(AbsListView view, int scrollState) {}

                    @Override
                    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                        if (!isSuggestionsLoading && (firstVisibleItem + visibleItemCount >= totalItemCount - 4) && currentSuggestionsPage < totalSuggestionsPages) {
                            currentSuggestionsPage++;
                            String typePath = isTvShow ? "tv" : "movie";
                            String recommendationsUrl = "https://api.themoviedb.org/3/" + typePath + "/" + contentId + "/recommendations?api_key=" + API_KEY + "&page=" + currentSuggestionsPage;
                            new FetchSuggestionsTask().execute(recommendationsUrl);
                        }
                    }
                });
        }
    }

    private void updateWatchLaterButtonUI() {
        if (btnWatchLater == null) return;

        SharedPreferences prefs = getSharedPreferences("HeziFlixPrefs", Context.MODE_PRIVATE);
        String savedJson = prefs.getString("watch_later_list", "[]");
        boolean matchesStoredItem = false;

        try {
            JSONArray array = new JSONArray(savedJson);
            for (int i = 0; i < array.length(); i++) {
                if (array.getJSONObject(i).getString("id").equals(contentId)) {
                    matchesStoredItem = true;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (matchesStoredItem) {
            btnWatchLater.setText("REMOVE LATER");
        } else {
            btnWatchLater.setText("WATCH LATER");
        }
    }

    private void executeWatchLaterToggleAction() {
        SharedPreferences prefs = getSharedPreferences("HeziFlixPrefs", Context.MODE_PRIVATE);
        String savedJson = prefs.getString("watch_later_list", "[]");

        try {
            JSONArray array = new JSONArray(savedJson);
            int matchingIndex = -1;

            for (int i = 0; i < array.length(); i++) {
                if (array.getJSONObject(i).getString("id").equals(contentId)) {
                    matchingIndex = i;
                    break;
                }
            }

            if (matchingIndex != -1) {
                array.remove(matchingIndex);
                prefs.edit().putString("watch_later_list", array.toString()).apply();
                btnWatchLater.setText("WATCH LATER");
                Toast.makeText(this, "Removed from Watch Later list.", Toast.LENGTH_SHORT).show();
            } else {
                JSONObject obj = new JSONObject();
                obj.put("id", contentId);
                obj.put("title", tvTitle.getText().toString());
                obj.put("overview", tvOverview.getText().toString());
                obj.put("rating", savedRatingNumericValue);
                obj.put("poster_path", savedRawPosterPath);
                obj.put("is_tv", isTvShow);

                array.put(obj);
                prefs.edit().putString("watch_later_list", array.toString()).apply();
                btnWatchLater.setText("REMOVE LATER");
                Toast.makeText(this, "Saved to Watch Later list!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save data package.", Toast.LENGTH_SHORT).show();
        }
    }

    // Watch History engine: adds clean entries to the top and limits storage load size to 50 items
    private void addToWatchHistory(String id, String title, String overview, double rating, String posterPath, boolean isTv) {
        SharedPreferences prefs = getSharedPreferences("HeziFlixPrefs", Context.MODE_PRIVATE);
        String savedJson = prefs.getString("watch_history_list", "[]");
        try {
            JSONArray oldArray = new JSONArray(savedJson);
            JSONArray newArray = new JSONArray();

            JSONObject currentViewing = new JSONObject();
            currentViewing.put("id", id);
            currentViewing.put("title", title);
            currentViewing.put("overview", overview);
            currentViewing.put("rating", rating);
            currentViewing.put("poster_path", posterPath);
            currentViewing.put("is_tv", isTv);

            newArray.put(currentViewing);

            // Filter out existing instances of this item to put it right at the top
            for (int i = 0; i < oldArray.length(); i++) {
                JSONObject obj = oldArray.getJSONObject(i);
                if (!obj.getString("id").equals(id)) {
                    newArray.put(obj);
                }
            }

            // Slice out tail variables if the history goes over 50 items
            if (newArray.length() > 50) {
                JSONArray truncatedArray = new JSONArray();
                for (int i = 0; i < 50; i++) {
                    truncatedArray.put(newArray.getJSONObject(i));
                }
                newArray = truncatedArray;
            }

            prefs.edit().putString("watch_history_list", newArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadVideoStreamingScreen() {
        setContentView(R.layout.video_player);

        playerWebView = (WebView) findViewById(R.id.trailer_webview);
        progressBar = (ProgressBar) findViewById(R.id.player_progress);
        selectorContainer = (LinearLayout) findViewById(R.id.selector_container);
        spinnerSeason = (Spinner) findViewById(R.id.spinner_season);
        spinnerEpisode = (Spinner) findViewById(R.id.spinner_episode);
        btnToggleSelectors = (Button) findViewById(R.id.btn_toggle_selectors);

        WebSettings settings = playerWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        playerWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    progressBar.setVisibility(View.VISIBLE);
                }
                @Override
                public void onPageFinished(WebView view, String url) {
                    progressBar.setVisibility(View.GONE);
                }
            });

        playerWebView.setWebChromeClient(new WebChromeClient());

        // Force the WebView to occupy the full screen
        ViewGroup.LayoutParams lp = playerWebView.getLayoutParams();
        if (lp != null) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            playerWebView.setLayoutParams(lp);
        } else {
            playerWebView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // Apply immersive fullscreen mode
        enableImmersiveFullscreen();

        if (isTvShow) {
            // Show selector container for TV shows by default
            if (selectorContainer != null) selectorContainer.setVisibility(View.VISIBLE);
            setupTvSpinners();
        } else {
            // Hide selector container for movies
            if (selectorContainer != null) selectorContainer.setVisibility(View.GONE);
            updateVideoFrameUrl();
        }

        // Setup toggle button to show/hide selectors
        if (btnToggleSelectors != null) {
            btnToggleSelectors.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectorContainer != null) {
                        if (selectorContainer.getVisibility() == View.VISIBLE) {
                            selectorContainer.setVisibility(View.GONE);
                        } else {
                            selectorContainer.setVisibility(View.VISIBLE);
                        }
                        // Reapply immersive so system UI stays hidden after toggling
                        enableImmersiveFullscreen();
                    }
                }
            });
        }
    }

    /**
     * Enable immersive full-screen mode across Android versions.
     */
    private void enableImmersiveFullscreen() {
        final View decor = getWindow().getDecorView();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Modern API
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                // Legacy flags for older devices
                int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
					| View.SYSTEM_UI_FLAG_FULLSCREEN
					| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                decor.setSystemUiVisibility(flags);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        } catch (Exception e) {
            // Fallback: try legacy flags if anything fails
            try {
                int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
					| View.SYSTEM_UI_FLAG_FULLSCREEN
					| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                decor.setSystemUiVisibility(flags);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reapply immersive fullscreen in case it was lost
        enableImmersiveFullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveFullscreen();
        }
    }

    private void setupTvSpinners() {
        seasonList.clear();
        if (seasonsJsonArray != null) {
            for (int i = 0; i < seasonsJsonArray.length(); i++) {
                try {
                    JSONObject sObj = seasonsJsonArray.getJSONObject(i);
                    int sNum = sObj.getInt("season_number");
                    if (sNum > 0) seasonList.add(sNum);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (seasonList.isEmpty()) seasonList.add(1);

        ArrayAdapter<Integer> seasonAdapter = new ArrayAdapter<Integer>(this, android.R.layout.simple_spinner_item, seasonList);
        seasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSeason.setAdapter(seasonAdapter);

        spinnerSeason.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedSeasonNum = seasonList.get(position);
                    fetchEpisodesCount(selectedSeasonNum);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
    }

    private void fetchEpisodesCount(int seasonNum) {
        String epUrl = "https://api.themoviedb.org/3/tv/" + contentId + "/season/" + seasonNum + "?api_key=" + API_KEY;
        new FetchEpisodesTask().execute(epUrl);
    }

    private void updateVideoFrameUrl() {
        String embedUrl = "";
        if (isTvShow) {
            embedUrl = "https://player.videasy.net/tv/" + contentId + "/" + selectedSeasonNum + "/" + selectedEpisodeNum;
        } else {
            embedUrl = "https://player.videasy.net/movie/" + contentId;
        }
        if (playerWebView != null) playerWebView.loadUrl(embedUrl);
    }

    private class FetchDetailsTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            StringBuilder b = new StringBuilder();
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urls[0]).openConnection();
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) b.append(line);
                rd.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return b.toString();
        }

        @Override
        protected void onPostExecute(String res) {
            try {
                JSONObject json = new JSONObject(res);
                String titleStr = isTvShow ? json.getString("name") : json.getString("title");
                String dateStr = isTvShow ? json.optString("first_air_date", "----") : json.optString("release_date", "----");
                String yearStr = (dateStr.length() >= 4) ? dateStr.substring(0, 4) : "----";

                tvTitle.setText(titleStr.toUpperCase());
                tvOverview.setText(json.optString("overview", "No cinematic plot configuration details specified."));

                if (tvYear != null) tvYear.setText(yearStr);

                savedRatingNumericValue = json.optDouble("vote_average", 0.0);
                if (tvRatingStars != null) {
                    tvRatingStars.setText(String.format("★ %.1f", savedRatingNumericValue));
                }

                savedRawPosterPath = json.optString("poster_path", "");

                if (json.has("seasons")) {
                    seasonsJsonArray = json.getJSONArray("seasons");
                }

                String backdropPath = json.optString("backdrop_path", "");
                if (!backdropPath.isEmpty()) {
                    new LoadImageBitmapTask(ivBackdrop).execute("https://image.tmdb.org/t/p/w780" + backdropPath);
                }

                // Automatically commit this object directly into local History arrays
                addToWatchHistory(contentId, titleStr, json.optString("overview", ""), savedRatingNumericValue, savedRawPosterPath, isTvShoimport java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public class PlayerActivity extends BaseActivity {

    private final String API_KEY = "049e000306b8490d040be01ddda7abf4";

    private ImageView ivBackdrop;
    private TextView tvTitle, tvOverview, tvRatingStars, tvYear;
    private Button btnWatchNow, btnWatchLater; 
    private ListView lvSuggestions;
    private SuggestionAdapter suggestionAdapter;
    private ArrayList<MovieSuggestion> suggestionsList = new ArrayList<MovieSuggestion>();

    private int currentSuggestionsPage = 1;
    private int totalSuggestionsPages = 1;
    private boolean isSuggestionsLoading = false;

    private WebView playerWebView;
    private ProgressBar progressBar;
    private LinearLayout selectorContainer;
    private LinearLayout landscapeControlSidebar;
    private Spinner spinnerSeason;
    private Spinner spinnerEpisode;
    private Button btnToggleSelectors;

    private String contentId;
    private boolean isTvShow;

    private String savedRawPosterPath = "";
    private double savedRatingNumericValue = 0.0;

    private ArrayList<Integer> seasonList = new ArrayList<Integer>();
    private ArrayList<Integer> episodeList = new ArrayList<Integer>();
    private int selectedSeasonNum = 1;
    private int selectedEpisodeNum = 1;
    private JSONArray seasonsJsonArray = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        contentId = getIntent().getStringExtra("MOVIE_ID");
        isTvShow = getIntent().getBooleanExtra("IS_TV", false);

        loadOverviewDetailsScreen();
    }

    private void loadOverviewDetailsScreen() {
        setContentView(R.layout.activity_info_panel);

        ivBackdrop = (ImageView) findViewById(R.id.detail_backdrop);
        tvTitle = (TextView) findViewById(R.id.detail_title);
        tvOverview = (TextView) findViewById(R.id.detail_overview);
        tvRatingStars = (TextView) findViewById(R.id.detail_rating_stars);
        tvYear = (TextView) findViewById(R.id.detail_year);
        btnWatchNow = (Button) findViewById(R.id.btn_watch_now);
        btnWatchLater = (Button) findViewById(R.id.btn_add_list); 
        lvSuggestions = (ListView) findViewById(R.id.lv_suggestions);

        currentSuggestionsPage = 1;
        totalSuggestionsPages = 1;
        suggestionsList.clear();
        suggestionAdapter = new SuggestionAdapter(this, suggestionsList);
        if (lvSuggestions != null) {
            lvSuggestions.setAdapter(suggestionAdapter);
        }

        updateWatchLaterButtonUI();

        String typePath = isTvShow ? "tv" : "movie";
        String detailsUrl = "https://api.themoviedb.org/3/" + typePath + "/" + contentId + "?api_key=" + API_KEY;
        new FetchDetailsTask().execute(detailsUrl);

        btnWatchNow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadVideoStreamingScreen();
                }
            });

        if (btnWatchLater != null) {
            btnWatchLater.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        executeWatchLaterToggleAction();
                    }
                });
        }

        if (lvSuggestions != null) {
            lvSuggestions.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        MovieSuggestion tapped = suggestionsList.get(position);
                        contentId = tapped.getId();
                        loadOverviewDetailsScreen();
                    }
                });

            lvSuggestions.setOnScrollListener(new AbsListView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(AbsListView view, int scrollState) {}

                    @Override
                    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                        if (!isSuggestionsLoading && (firstVisibleItem + visibleItemCount >= totalItemCount - 4) && currentSuggestionsPage < totalSuggestionsPages) {
                            currentSuggestionsPage++;
                            String typePath = isTvShow ? "tv" : "movie";
                            String recommendationsUrl = "https://api.themoviedb.org/3/" + typePath + "/" + contentId + "/recommendations?api_key=" + API_KEY + "&page=" + currentSuggestionsPage;
                            new FetchSuggestionsTask().execute(recommendationsUrl);
                        }
                    }
                });
        }
    }

    private void updateWatchLaterButtonUI() {
        if (btnWatchLater == null) return;

        SharedPreferences prefs = getSharedPreferences("HeziFlixPrefs", Context.MODE_PRIVATE);
        String savedJson = prefs.getString("watch_later_list", "[]");
        boolean matchesStoredItem = false;

        try {
            JSONArray array = new JSONArray(savedJson);
            for (int i = 0; i < array.length(); i++) {
                if (array.getJSONObject(i).getString("id").equals(contentId)) {
                    matchesStoredItem = true;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (matchesStoredItem) {
            btnWatchLater.setText("REMOVE LATER");
        } else {
            btnWatchLater.setText("WATCH LATER");
        }
    }

    private void executeWatchLaterToggleAction() {
        SharedPreferences prefs = getSharedPreferences("HeziFlixPrefs", Context.MODE_PRIVATE);
        String savedJson = prefs.getString("watch_later_list", "[]");

        try {
            JSONArray array = new JSONArray(savedJson);
            int matchingIndex = -1;

            for (int i = 0; i < array.length(); i++) {
                if (array.getJSONObject(i).getString("id").equals(contentId)) {
                    matchingIndex = i;
                    break;
                }
            }

            if (matchingIndex != -1) {
                array.remove(matchingIndex);
                prefs.edit().putString("watch_later_list", array.toString()).apply();
                btnWatchLater.setText("WATCH LATER");
                Toast.makeText(this, "Removed from Watch Later list.", Toast.LENGTH_SHORT).show();
            } else {
                JSONObject obj = new JSONObject();
                obj.put("id", contentId);
                obj.put("title", tvTitle.getText().toString());
                obj.put("overview", tvOverview.getText().toString());
                obj.put("rating", savedRatingNumericValue);
                obj.put("poster_path", savedRawPosterPath);
                obj.put("is_tv", isTvShow);

                array.put(obj);
                prefs.edit().putString("watch_later_list", array.toString()).apply();
                btnWatchLater.setText("REMOVE LATER");
                Toast.makeText(this, "Saved to Watch Later list!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save data package.", Toast.LENGTH_SHORT).show();
        }
    }

    // Watch History engine: adds clean entries to the top and limits storage load size to 50 items
    private void addToWatchHistory(String id, String title, String overview, double rating, String posterPath, boolean isTv) {
        SharedPreferences prefs = getSharedPreferences("HeziFlixPrefs", Context.MODE_PRIVATE);
        String savedJson = prefs.getString("watch_history_list", "[]");
        try {
            JSONArray oldArray = new JSONArray(savedJson);
            JSONArray newArray = new JSONArray();

            JSONObject currentViewing = new JSONObject();
            currentViewing.put("id", id);
            currentViewing.put("title", title);
            currentViewing.put("overview", overview);
            currentViewing.put("rating", rating);
            currentViewing.put("poster_path", posterPath);
            currentViewing.put("is_tv", isTv);

            newArray.put(currentViewing);

            // Filter out existing instances of this item to put it right at the top
            for (int i = 0; i < oldArray.length(); i++) {
                JSONObject obj = oldArray.getJSONObject(i);
                if (!obj.getString("id").equals(id)) {
                    newArray.put(obj);
                }
            }

            // Slice out tail variables if the history goes over 50 items
            if (newArray.length() > 50) {
                JSONArray truncatedArray = new JSONArray();
                for (int i = 0; i < 50; i++) {
                    truncatedArray.put(newArray.getJSONObject(i));
                }
                newArray = truncatedArray;
            }

            prefs.edit().putString("watch_history_list", newArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadVideoStreamingScreen() {
        setContentView(R.layout.video_player);

        playerWebView = (WebView) findViewById(R.id.trailer_webview);
        progressBar = (ProgressBar) findViewById(R.id.player_progress);
        selectorContainer = (LinearLayout) findViewById(R.id.selector_container);
        spinnerSeason = (Spinner) findViewById(R.id.spinner_season);
        spinnerEpisode = (Spinner) findViewById(R.id.spinner_episode);

        WebSettings settings = playerWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        playerWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    progressBar.setVisibility(View.VISIBLE);
                }
                @Override
                public void onPageFinished(WebView view, String url) {
                    progressBar.setVisibility(View.GONE);
                }
            });

        playerWebView.setWebChromeClient(new WebChromeClient());

        // Force the WebView to occupy the full screen
        ViewGroup.LayoutParams lp = playerWebView.getLayoutParams();
        if (lp != null) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            playerWebView.setLayoutParams(lp);
        } else {
            playerWebView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // Default: hide selector UI so the WebView is visually fullscreen.
        if (selectorContainer != null) selectorContainer.setVisibility(View.GONE);

        // Apply immersive fullscreen mode
        enableImmersiveFullscreen();

        if (isTvShow) {
            selectorContainer.setVisibility(View.VISIBLE);
            setupTvSpinners();
        } else {
            selectorContainer.setVisibility(View.GONE);
            updateVideoFrameUrl();
        }
    }

    /**
     * Enable immersive full-screen mode across Android versions.
     */
    private void enableImmersiveFullscreen() {
        final View decor = getWindow().getDecorView();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Modern API
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                // Legacy flags for older devices
                int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
					| View.SYSTEM_UI_FLAG_FULLSCREEN
					| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                decor.setSystemUiVisibility(flags);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        } catch (Exception e) {
            // Fallback: try legacy flags if anything fails
            try {
                int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
					| View.SYSTEM_UI_FLAG_FULLSCREEN
					| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                decor.setSystemUiVisibility(flags);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reapply immersive fullscreen in case it was lost
        enableImmersiveFullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveFullscreen();
        }
    }

    private void setupTvSpinners() {
        seasonList.clear();
        if (seasonsJsonArray != null) {
            for (int i = 0; i < seasonsJsonArray.length(); i++) {
                try {
                    JSONObject sObj = seasonsJsonArray.getJSONObject(i);
                    int sNum = sObj.getInt("season_number");
                    if (sNum > 0) seasonList.add(sNum);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (seasonList.isEmpty()) seasonList.add(1);

        ArrayAdapter<Integer> seasonAdapter = new ArrayAdapter<Integer>(this, android.R.layout.simple_spinner_item, seasonList);
        seasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSeason.setAdapter(seasonAdapter);

        spinnerSeason.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedSeasonNum = seasonList.get(position);
                    fetchEpisodesCount(selectedSeasonNum);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
    }

    private void fetchEpisodesCount(int seasonNum) {
        String epUrl = "https://api.themoviedb.org/3/tv/" + contentId + "/season/" + seasonNum + "?api_key=" + API_KEY;
        new FetchEpisodesTask().execute(epUrl);
    }

    private void updateVideoFrameUrl() {
        String embedUrl = "";
        if (isTvShow) {
            embedUrl = "https://player.videasy.net/tv/" + contentId + "/" + selectedSeasonNum + "/" + selectedEpisodeNum;
        } else {
            embedUrl = "https://player.videasy.net/movie/" + contentId;
        }
        if (playerWebView != null) playerWebView.loadUrl(embedUrl);
    }

    private class FetchDetailsTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            StringBuilder b = new StringBuilder();
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urls[0]).openConnection();
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) b.append(line);
                rd.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return b.toString();
        }

        @Override
        protected void onPostExecute(String res) {
            try {
                JSONObject json = new JSONObject(res);
                String titleStr = isTvShow ? json.getString("name") : json.getString("title");
                String dateStr = isTvShow ? json.optString("first_air_date", "----") : json.optString("release_date", "----");
                String yearStr = (dateStr.length() >= 4) ? dateStr.substring(0, 4) : "----";

                tvTitle.setText(titleStr.toUpperCase());
                tvOverview.setText(json.optString("overview", "No cinematic plot configuration details specified."));

                if (tvYear != null) tvYear.setText(yearStr);

                savedRatingNumericValue = json.optDouble("vote_average", 0.0);
                if (tvRatingStars != null) {
                    tvRatingStars.setText(String.format("★ %.1f", savedRatingNumericValue));
                }

                savedRawPosterPath = json.optString("poster_path", "");

                if (json.has("seasons")) {
                    seasonsJsonArray = json.getJSONArray("seasons");
                }

                String backdropPath = json.optString("backdrop_path", "");
                if (!backdropPath.isEmpty()) {
                    new LoadImageBitmapTask(ivBackdrop).execute("https://image.tmdb.org/t/p/w780" + backdropPath);
                }

                // Automatically commit this object directly into local History arrays
                addToWatchHistory(contentId, titleStr, json.optString("overview", ""), savedRatingNumericValue, savedRawPosterPath, isTvShow);

                String typePath = isTvShow ? "tv" : "movie";
                String recommendationsUrl = "https://api.themoviedb.org/3/" + typePath + "/" + contentId + "/recommendations?api_key=" + API_KEY + "&page=" + currentSuggestionsPage;
                new FetchSuggestionsTask().execute(recommendationsUrl);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class FetchSuggestionsTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            isSuggestionsLoading = true;
        }

        @Override
        protected String doInBackground(String... urls) {
            StringBuilder b = new StringBuilder();
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urls[0]).openConnection();
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) b.append(line);
                rd.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return b.toString();
        }

        @Override
        protected void onPostExecute(String res) {
            try {
                JSONObject json = new JSONObject(res);
                totalSuggestionsPages = json.optInt("total_pages", 1);
                JSONArray results = json.getJSONArray("results");
                for (int i = 0; i < results.length(); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    String nameStr = obj.has("title") ? obj.getString("title") : obj.getString("name");
                    String dateStr = obj.has("release_date") ? obj.optString("release_date", "----") : obj.optString("first_air_date", "----");
                    String yearStr = (dateStr.length() >= 4) ? dateStr.substring(0, 4) : "----";

                    MovieSuggestion suggest = new MovieSuggestion(
                        obj.getString("id"),
                        nameStr,
                        yearStr,
                        obj.optDouble("vote_average", 0.0),
                        obj.optString("backdrop_path", "")
                    );
                    suggestionsList.add(suggest);
                }
                suggestionAdapter.notifyDataSetChanged();
            } catch (Exception e) {
                e.printStackTrace();
            }
            isSuggestionsLoading = false;
        }
    }

    private class FetchEpisodesTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            StringBuilder b = new StringBuilder();
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urls[0]).openConnection();
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) b.append(line);
                rd.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return b.toString();
        }

        @Override
        protected void onPostExecute(String res) {
            try {
                JSONObject json = new JSONObject(res);
                JSONArray eps = json.getJSONArray("episodes");
                episodeList.clear();
                for (int i = 0; i < eps.length(); i++) {
                    episodeList.add(i + 1);
                }
                if (episodeList.isEmpty()) episodeList.add(1);

                ArrayAdapter<Integer> epAdapter = new ArrayAdapter<Integer>(PlayerActivity.this, android.R.layout.simple_spinner_item, episodeList);
                epAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerEpisode.setAdapter(epAdapter);

                spinnerEpisode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            selectedEpisodeNum = episodeList.get(position);
                            updateVideoFrameUrl();
                        }
                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class LoadImageBitmapTask extends AsyncTask<String, Void, Bitmap> {
        private ImageView view;

        public LoadImageBitmapTask(ImageView view) { this.view = view; }

        @Override
        protected Bitmap doInBackground(String... urls) {
            Bitmap b = null;
            try {
                b = BitmapFactory.decodeStream(new java.net.URL(urls[0]).openStream());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return b;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result != null && view != null) view.setImageBitmap(result);
        }
    }

    private static class MovieSuggestion {
        private String id, title, year, backdropPath;
        private double rating;

        public MovieSuggestion(String id, String title, String year, double rating, String backdropPath) {
            this.id = id;
            this.title = title;
            this.year = year;
            this.rating = rating;
            this.backdropPath = backdropPath;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getYear() { return year; }
        public double getRating() { return rating; }
        public String getThumbUrl() { return "https://image.tmdb.org/t/p/w300" + backdropPath; }
    }

    private class SuggestionAdapter extends BaseAdapter {
        private Context ctx;
        private ArrayList<MovieSuggestion> list;

        public SuggestionAdapter(Context ctx, ArrayList<MovieSuggestion> list) {
            this.ctx = ctx;
            this.list = list;
        }

        @Override
        public int getCount() { return list.size(); }
        @Override
        public Object getItem(int pos) { return list.get(pos); }
        @Override
        public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            ViewHolder holder;
            if (convert == null) {
                convert = LayoutInflater.from(ctx).inflate(R.layout.suggestion_item, parent, false);
                holder = new ViewHolder();
                holder.thumb = (ImageView) convert.findViewById(R.id.suggest_thumb);
                holder.title = (TextView) convert.findViewById(R.id.suggest_title);
                holder.year = (TextView) convert.findViewById(R.id.suggest_year);
                holder.rating = (TextView) convert.findViewById(R.id.suggest_rating);
                convert.setTag(holder);
            } else {
                holder = (ViewHolder) convert.getTag();
            }

            MovieSuggestion item = list.get(pos);
            holder.title.setText(item.getTitle());
            holder.year.setText(item.getYear());
            holder.rating.setText(String.format("★ %.1f", item.getRating()));

            holder.thumb.setImageBitmap(null);
            if (item.backdropPath != null && !item.backdropPath.isEmpty()) {
                new LoadImageBitmapTask(holder.thumb).execute(item.getThumbUrl());
            }

            return convert;
        }

        class ViewHolder {
            ImageView thumb;
            TextView title, year, rating;
        }
    }

    @Override
    protected void onDestroy() {
        if (playerWebView != null) {
            playerWebView.loadUrl("about:blank");
            playerWebView.destroy();
        }
        super.onDestroy();
    }
}
