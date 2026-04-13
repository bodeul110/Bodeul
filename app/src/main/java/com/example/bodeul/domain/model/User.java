package com.example.bodeul.domain.model;

/**
 * 로그인 사용자와 도메인 참여자의 공통 정보를 표현한다.
 */
public class User {
    // 사용자 식별과 권한 판별에 쓰는 기본 정보다.
    private final String id;
    private final UserRole role;

    // 화면 표시와 연락에 사용하는 프로필 정보다.
    private final String name;
    private final String email;
    private final String phone;

    public User(String id, UserRole role, String name, String email, String phone) {
        this.id = id;
        this.role = role;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public String getId() {
        return id;
    }

    public UserRole getRole() {
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
