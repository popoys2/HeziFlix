package com.hezi.flix;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends BaseActivity {

    private final String API_KEY = "049e000306b8490d040be01ddda7abf4"; 

    private int userPrimaryColor = Color.parseColor("#FFD700");   
    private int userBackgroundColor = Color.parseColor("#12161A"); 

    private ArrayList<Movie> movieList = new ArrayList<Movie>();
    private MovieAdapter adapter;
    private LruCache<String, Bitmap> imageCache;

    private int currentPage = 1;
    private boolean isLoading = false;
    private String currentCategoryUrl = "";
    private boolean isSearchMode = false;
    private boolean isWatchLaterMode = false; 
    private boolean isWatchHistoryMode = false; 

    private String currentTypeMode = "tv"; 
    private String currentSelectedCategoryName = "Popular";

    private RelativeLayout rootLayout, toolbar;
    private TextView appTitle, tvDrawerTitle, tvSectionMainTitle;
    private Button btnHamburger, btnThemeToggle, btnSearchToggle, btnSearchSubmit;
    private Button btnTypeMovies, btnTypeSeries;
    private Button btnWatchLaterToggle; 
    private Button btnWatchHistoryToggle; 
    private Button btnFb, btnInsta, btnTiktok; 
    private LinearLayout searchContainer, navDrawer, categoryButtonsContainer;
    private EditText etSearch;
    private GridView movieGrid;

    private int selectedColorTicker = Color.RED;

    private final String[] categories = {
        "Popular", "Top Rated", "Upcoming", "Action", "Adventure", "Animation", "Comedy", "Crime", 
        "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Music", "Mystery", 
        "Romance", "Science Fiction", "Thriller", "War", "Western", "Anime", "K-Drama"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        imageCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        rootLayout = (RelativeLayout) findViewById(R.id.main_root);
        toolbar = (RelativeLayout) findViewById(R.id.toolbar);
        appTitle = (TextView) findViewById(R.id.app_title);
        tvDrawerTitle = (TextView) findViewById(R.id.tv_drawer_title);
        tvSectionMainTitle = (TextView) findViewById(R.id.tv_section_main_title);

        btnHamburger = (Button) findViewById(R.id.btn_hamburger);
        btnThemeToggle = (Button) findViewById(R.id.btn_theme_toggle);
        btnSearchToggle = (Button) findViewById(R.id.btn_search_toggle);
        btnWatchLaterToggle = (Button) findViewById(R.id.btn_watch_later_toggle); 
        btnWatchHistoryToggle = (Button) findViewById(R.id.btn_watch_history_toggle); 
        btnSearchSubmit = (Button) findViewById(R.id.btn_search_submit);

        btnTypeMovies = (Button) findViewById(R.id.btn_type_movies);
        btnTypeSeries = (Button) findViewById(R.id.btn_type_series);

        btnFb = (Button) findViewById(R.id.btn_fb);
        btnInsta = (Button) findViewById(R.id.btn_insta);
        btnTiktok = (Button) findViewById(R.id.btn_tiktok);

        searchContainer = (LinearLayout) findViewById(R.id.search_container);
        navDrawer = (LinearLayout) findViewById(R.id.nav_drawer);
        categoryButtonsContainer = (LinearLayout) findViewById(R.id.category_buttons_container);
        etSearch = (EditText) findViewById(R.id.et_search);
        movieGrid = (GridView) findViewById(R.id.movie_grid);

        adapter = new MovieAdapter(this, movieList);
        movieGrid.setAdapter(adapter);

        setupCategoryButtons();
        updateCategorySelectionUI();

        String discoverUrl = "https://api.themoviedb.org/3/discover/" + currentTypeMode + "?api_key=" + API_KEY + "&sort_by=popularity.desc&page=" + currentPage;
        currentCategoryUrl = discoverUrl;
        new FetchMoviesTask().execute(discoverUrl);

        btnTypeMovies.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					isWatchLaterMode = false;
					isWatchHistoryMode = false;
					if (!currentTypeMode.equals("movie")) {
						currentTypeMode = "movie";
						resetAndFetchCategory(currentSelectedCategoryName);
					} else {
						resetAndFetchCategory(currentSelectedCategoryName);
					}
				}
			});

        btnTypeSeries.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					isWatchLaterMode = false;
					isWatchHistoryMode = false;
					if (!currentTypeMode.equals("tv")) {
						currentTypeMode = "tv";
						resetAndFetchCategory(currentSelectedCategoryName);
					} else {
						resetAndFetchCategory(currentSelectedCategoryName);
					}
				}
			});

        btnHamburger.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (navDrawer.getVisibility() == View.GONE) {
						navDrawer.setVisibility(View.VISIBLE);
					} else {
						navDrawer.setVisibility(View.GONE);
					}
				}
			});

        btnWatchLaterToggle.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					searchContainer.setVisibility(View.GONE);
					isSearchMode = false;
					isWatchHistoryMode = false;
					loadWatchLaterLocalData();
				}
			});

        btnWatchHistoryToggle.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					searchContainer.setVisibility(View.GONE);
					isSearchMode = false;
					isWatchLaterMode = false;
					loadWatchHistoryLocalData();
				}
			});

        btnSearchToggle.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (searchContainer.getVisibility() == View.GONE) {
						searchContainer.setVisibility(View.VISIBLE);
						isSearchMode = true;
					} else {
						searchContainer.setVisibility(View.GONE);
						isSearchMode = false;
						isWatchLaterMode = false;
						isWatchHistoryMode = false;
						resetAndFetchCategory(currentSelectedCategoryName);
					}
				}
			});

        btnSearchSubmit.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					isWatchLaterMode = false;
					isWatchHistoryMode = false;
					performSearchQuery();
				}
			});

        btnThemeToggle.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openWheelPickerSequence();
				}
			});

        movieGrid.setOnScrollListener(new AbsListView.OnScrollListener() {
				@Override
				public void onScrollStateChanged(AbsListView view, int scrollState) {}

				@Override
				public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
					if (isWatchLaterMode || isWatchHistoryMode) return;

					if (!isLoading && (firstVisibleItem + visibleItemCount >= totalItemCount - 16) && totalItemCount > 0) {
						currentPage++;
						String nextUrl = currentCategoryUrl + "&page=" + currentPage;
						new FetchMoviesTask().execute(nextUrl);
					}
				}
			});

        movieGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					Movie selected = movieList.get(position);
					Intent i = new Intent(MainActivity.this, PlayerActivity.class);
					i.putExtra("MOVIE_ID", selected.getId());

					if (isWatchLaterMode || isWatchHistoryMode) {
						i.putExtra("IS_TV", selected.isTv());
					} else {
						i.putExtra("IS_TV", currentTypeMode.equals("tv"));
					}
					startActivity(i);
				}
			});

        setupSocialMediaLinks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isWatchLaterMode) {
            loadWatchLaterLocalData();
        } else if (isWatchHistoryMode) {
            loadWatchHistoryLocalData();
        }
    }

    private void loadWatchLaterLocalData() {
        isWatchLaterMode = true;
        isWatchHistoryMode = false;
        tvSectionMainTitle.setText("My Watch Later List");

        btnWatchLaterToggle.setTextColor(userPrimaryColor);
        btnWatchHistoryToggle.setTextColor(Color.parseColor("#8A929A"));
        btnTypeMovies.setTextColor(Color.parseColor("#8A929A"));
        btnTypeSeries.setTextColor(Color.parseColor("#8A929A"));
        for (int i = 0; i < categoryButtonsContainer.getChildCount(); i++) {
            ((Button) categoryButtonsContainer.getChildAt(i)).setTextColor(Color.parseColor("#8A929A"));
        }

        movieList.clear();
        SharedPreferences prefs = getSharedPreferences("HeziFlixPrefs", Context.MODE_PRIVATE);
        String savedJson = prefs.getString("watch_later_list", "[]");

        try {
            JSONArray array = new JSONArray(savedJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Movie m = new Movie(
                    obj.getString("id"),
                    obj.getString("title"),
                    obj.optString("poster_path", ""),
                    obj.optString("overview", "")
                );
                m.setRating(obj.optDouble("rating", 0.0));
                m.setTv(obj.optBoolean("is_tv", false));
                movieList.add(m);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        adapter.notifyDataSetChanged();
        if (movieList.isEmpty()) {
            Toast.makeText(this, "Your Watch Later folder is empty.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadWatchHistoryLocalData() {
        isWatchHistoryMode = true;
        isWatchLaterMode = false;
        tvSectionMainTitle.setText("Watch History");

        btnWatchHistoryToggle.setTextColor(userPrimaryColor);
        btnWatchLaterToggle.setTextColor(Color.parseColor("#8A929A"));
        btnTypeMovies.setTextColor(Color.parseColor("#8A929A"));
        btnTypeSeries.setTextColor(Color.parseColor("#8A929A"));
        for (int i = 0; i < categoryButtonsContainer.getChildCount(); i++) {
            ((Button) categoryButtonsContainer.getChildAt(i)).setTextColor(Color.parseColor("#8A929A"));
        }

        movieList.clear();
        SharedPreferences prefs = getSharedPreferences("HeziFlixPrefs", Context.MODE_PRIVATE);
        String savedJson = prefs.getString("watch_history_list", "[]");

        try {
            JSONArray array = new JSONArray(savedJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Movie m = new Movie(
                    obj.getString("id"),
                    obj.getString("title"),
                    obj.optString("poster_path", ""),
                    obj.optString("overview", "")
                );
                m.setRating(obj.optDouble("rating", 0.0));
                m.setTv(obj.optBoolean("is_tv", false));
                movieList.add(m);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        adapter.notifyDataSetChanged();
        if (movieList.isEmpty()) {
            Toast.makeText(this, "No history found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSocialMediaLinks() {
        if (btnFb != null) {
            btnFb.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://facebook.com")));
					}
				});
        }
        if (btnInsta != null) {
            btnInsta.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com")));
					}
				});
        }
        if (btnTiktok != null) {
            btnTiktok.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://tiktok.com")));
					}
				});
        }
    }

    private void performSearchQuery() {
        String query = etSearch.getText().toString().trim();
        if (!query.isEmpty()) {
            isLoading = true;
            movieList.clear();
            adapter.notifyDataSetChanged();
            currentPage = 1;
            tvSectionMainTitle.setText("Search: " + query);

            String searchUrl = "https://api.themoviedb.org/3/search/" + currentTypeMode + "?api_key=" + API_KEY + "&query=" + Uri.encode(query);
            currentCategoryUrl = searchUrl;
            new FetchMoviesTask().execute(searchUrl + "&page=" + currentPage);
        }
    }

    private void setupCategoryButtons() {
        categoryButtonsContainer.removeAllViews();
        for (final String cat : categories) {
            Button b = new Button(this);
            b.setText(cat);
            b.setTextSize(11); 
            b.setTextColor(Color.parseColor("#8A929A"));
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setAllCaps(false);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMargins(0, 0, 14, 0);
            b.setLayoutParams(lp);

            b.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						searchContainer.setVisibility(View.GONE);
						isSearchMode = false;
						isWatchLaterMode = false;
						isWatchHistoryMode = false;
						resetAndFetchCategory(cat);
					}
				});

            categoryButtonsContainer.addView(b);
        }
    }

    private void resetAndFetchCategory(String categoryName) {
        currentSelectedCategoryName = categoryName;
        updateCategorySelectionUI();
        isLoading = true;
        currentPage = 1;
        movieList.clear();
        adapter.notifyDataSetChanged();

        String targetUrl = "https://api.themoviedb.org/3/discover/" + currentTypeMode + "?api_key=" + API_KEY + "&page=" + currentPage;
        String titlePrefix = currentTypeMode.equals("movie") ? "Movies" : "TV Series";
        tvSectionMainTitle.setText(titlePrefix + " - " + categoryName);

        if (categoryName.equals("Popular")) {
            targetUrl = "https://api.themoviedb.org/3/" + currentTypeMode + "/popular?api_key=" + API_KEY;
        } else if (categoryName.equals("Top Rated")) {
            targetUrl = "https://api.themoviedb.org/3/" + currentTypeMode + "/top_rated?api_key=" + API_KEY;
        } else if (categoryName.equals("Upcoming")) {
            if (currentTypeMode.equals("movie")) {
                targetUrl = "https://api.themoviedb.org/3/movie/upcoming?api_key=" + API_KEY;
            } else {
                targetUrl = "https://api.themoviedb.org/3/tv/on_the_air?api_key=" + API_KEY;
            }
        } else if (categoryName.equals("Anime")) {
            targetUrl += "&with_original_language=ja&with_keywords=210024|6075";
        } else if (categoryName.equals("K-Drama")) {
            targetUrl += "&with_original_language=ko";
        } else {
            String genreId = getGenreIdByName(categoryName);
            targetUrl += "&with_genres=" + genreId;
        }

        currentCategoryUrl = targetUrl;
        new FetchMoviesTask().execute(targetUrl + "&page=" + currentPage);
    }

    private void updateCategorySelectionUI() {
        btnWatchLaterToggle.setTextColor(Color.parseColor("#8A929A"));
        btnWatchHistoryToggle.setTextColor(Color.parseColor("#8A929A"));
        btnTypeMovies.setTextColor(currentTypeMode.equals("movie") ? userPrimaryColor : Color.parseColor("#8A929A"));
        btnTypeSeries.setTextColor(currentTypeMode.equals("tv") ? userPrimaryColor : Color.parseColor("#8A929A"));

        for (int i = 0; i < categoryButtonsContainer.getChildCount(); i++) {
            Button b = (Button) categoryButtonsContainer.getChildAt(i);
            if (b.getText().toString().equals(currentSelectedCategoryName) && !isWatchLaterMode && !isWatchHistoryMode) {
                b.setTextColor(userPrimaryColor);
            } else {
                b.setTextColor(Color.parseColor("#8A929A"));
            }
        }
    }

    private String getGenreIdByName(String name) {
        if (name.equals("Action")) return "28";
        if (name.equals("Adventure")) return "12";
        if (name.equals("Animation")) return "16";
        if (name.equals("Comedy")) return "35";
        if (name.equals("Crime")) return "80";
        if (name.equals("Documentary")) return "99";
        if (name.equals("Drama")) return "18";
        if (name.equals("Family")) return "10751";
        if (name.equals("Fantasy")) return "14";
        if (name.equals("History")) return "36";
        if (name.equals("Horror")) return "27";
        if (name.equals("Music")) return "10402";
        if (name.equals("Mystery")) return "96";
        if (name.equals("Romance")) return "10749";
        if (name.equals("Science Fiction")) return "878";
        if (name.equals("Thriller")) return "53";
        if (name.equals("War")) return "10752";
        if (name.equals("Western")) return "37";
        return "";
    }

    private void openWheelPickerSequence() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Customize Accent Colors");

        LinearLayout layoutContainer = new LinearLayout(this);
        layoutContainer.setOrientation(LinearLayout.VERTICAL);
        layoutContainer.setPadding(30, 20, 30, 20);

        final View colorDisplayNode = new View(this);
        LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80);
        viewParams.setMargins(0, 0, 0, 20);
        colorDisplayNode.setLayoutParams(viewParams);
        colorDisplayNode.setBackgroundColor(userPrimaryColor);
        layoutContainer.addView(colorDisplayNode);

        SeekBar hueSlider = new SeekBar(this);
        hueSlider.setMax(360);
        float[] currentHsv = new float[3];
        Color.colorToHSV(userPrimaryColor, currentHsv);
        hueSlider.setProgress((int) currentHsv[0]);
        layoutContainer.addView(hueSlider);

        hueSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					float[] hsv = new float[3];
					hsv[0] = progress; 
					hsv[1] = 1.0f;     
					hsv[2] = 1.0f;     
					selectedColorTicker = Color.HSVToColor(hsv);
					colorDisplayNode.setBackgroundColor(selectedColorTicker);
				}
				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {}
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {}
			});

        builder.setView(layoutContainer);
        builder.setPositiveButton("APPLY", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					userPrimaryColor = selectedColorTicker;
					appTitle.setTextColor(userPrimaryColor);
					tvSectionMainTitle.setTextColor(userPrimaryColor);
					if (isWatchLaterMode) {
						btnWatchLaterToggle.setTextColor(userPrimaryColor);
					} else if (isWatchHistoryMode) {
						btnWatchHistoryToggle.setTextColor(userPrimaryColor);
					} else {
						updateCategorySelectionUI();
					}
				}
			});
        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }

    private class FetchMoviesTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            isLoading = true;
        }

        @Override
        protected String doInBackground(String... urls) {
            StringBuilder response = new StringBuilder();
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urls[0]).openConnection();
                conn.setRequestMethod("GET");
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                }
                rd.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return response.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                JSONObject json = new JSONObject(result);
                JSONArray results = json.getJSONArray("results");
                for (int i = 0; i < results.length(); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    String titleName = obj.has("title") ? obj.getString("title") : obj.getString("name");
                    Movie movie = new Movie(
                        obj.getString("id"),
                        titleName,
                        obj.optString("poster_path", ""),
                        obj.optString("overview", "No synopsis configuration found.")
                    );
                    movie.setRating(obj.optDouble("vote_average", 0.0));
                    movieList.add(movie);
                }
                adapter.notifyDataSetChanged();
            } catch (Exception e) {
                e.printStackTrace();
            }
            isLoading = false;
        }
    }

    private class MovieAdapter extends BaseAdapter {
        private Context context;
        private ArrayList<Movie> list;

        public MovieAdapter(Context context, ArrayList<Movie> list) {
            this.context = context;
            this.list = list;
        }

        @Override
        public int getCount() { return list.size(); }
        @Override
        public Object getItem(int position) { return list.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.movie_item, parent, false);
                holder = new ViewHolder();
                holder.poster = (ImageView) convertView.findViewById(R.id.movie_poster);
                holder.title = (TextView) convertView.findViewById(R.id.movie_title);
                holder.rating = (TextView) convertView.findViewById(R.id.movie_rating);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            Movie current = list.get(position);
            holder.title.setText(current.getTitle());
            holder.rating.setText(String.format("★ Rating: %.1f", current.getRating()));

            holder.poster.setImageBitmap(null);
            String url = current.getPosterUrl();
            Bitmap cachedBitmap = imageCache.get(url);

            if (cachedBitmap != null) {
                holder.poster.setImageBitmap(cachedBitmap);
            } else {
                new LoadImageTask(holder.poster).execute(url);
            }

            return convertView;
        }

        class ViewHolder {
            ImageView poster;
            TextView title;
            TextView rating;
        }
    }

    private class LoadImageTask extends AsyncTask<String, Void, Bitmap> {
        private ImageView imageView;

        public LoadImageTask(ImageView imageView) {
            this.imageView = imageView;
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            String urlDisplay = urls[0];
            Bitmap bitmap = null;
            try {
                InputStream in = new java.net.URL(urlDisplay).openStream();
                bitmap = BitmapFactory.decodeStream(in);
                if (bitmap != null) {
                    imageCache.put(urlDisplay, bitmap);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result != null && imageView != null) {
                imageView.setImageBitmap(result);
            }
        }
    }
}


