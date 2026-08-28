package com.example.bodeul.ui.profile;

public final class ClientProfileScreenModel {
    private final String title;
    private final String subtitle;
    private final String heroTitle;
    private final String heroBody;
    private final String role;
    private final String name;
    private final String email;
    private final String phone;

    public ClientProfileScreenModel(
            String title,
            String subtitle,
            String heroTitle,
            String heroBody,
            String role,
            String name,
            String email,
            String phone
    ) {
        this.title = title;
        this.subtitle = subtitle;
        this.heroTitle = heroTitle;
        this.heroBody = heroBody;
        this.role = role;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getHeroTitle() {
        return heroTitle;
    }

    public String getHeroBody() {
        return heroBody;
    }

    public String getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }
}
