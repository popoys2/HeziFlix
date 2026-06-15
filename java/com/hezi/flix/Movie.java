package com.hezi.flix;

public class Movie {
    private String id;
    private String title;
    private String posterUrl;
    private String overview;
    private double rating; 
    private boolean isTv; // Track category type for local persistence routing

    public Movie(String id, String title, String posterUrl, String overview) {
        this.id = id;
        this.title = title;
        this.posterUrl = "https://image.tmdb.org/t/p/w342" + posterUrl;
        this.overview = overview;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getPosterUrl() { return posterUrl; }
    public String getOverview() { return overview; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public boolean isTv() { return isTv; }
    public void setTv(boolean isTv) { this.isTv = isTv; }
}
