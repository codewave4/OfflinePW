package com.offlinepw.vault.model;

public class VaultItem {
    private String id;
    private String title;
    private String category;
    private String username;
    private String password;
    private String notes;
    private boolean isFavorite;

    public VaultItem() {}

    public VaultItem(String id, String title, String category, String username, String password, String notes) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.username = username;
        this.password = password;
        this.notes = notes;
        this.isFavorite = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
}
