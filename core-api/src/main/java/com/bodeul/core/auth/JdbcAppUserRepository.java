package com.bodeul.core.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
class JdbcAppUserRepository implements AppUserRepository {

    private static final String FIND_BY_FIREBASE_UID = """
            select id, firebase_uid, role
            from bodeul.app_users
            where firebase_uid = ?
            limit 1
            """;
    private static final String FIND_BY_ID = """
            select id, firebase_uid, role
            from bodeul.app_users
            where id = ?
            limit 1
            """;

    private final JdbcTemplate jdbcTemplate;

    JdbcAppUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AppUser> findByFirebaseUid(String firebaseUid) {
        return find(FIND_BY_FIREBASE_UID, firebaseUid);
    }

    @Override
    public Optional<AppUser> findById(UUID id) {
        return find(FIND_BY_ID, id);
    }

    private Optional<AppUser> find(String sql, Object argument) {
        return jdbcTemplate.query(
                        sql,
                        (resultSet, rowNumber) -> new AppUser(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("firebase_uid"),
                                readRole(resultSet.getString("role"))),
                        argument)
                .stream()
                .findFirst();
    }

    private AppUserRole readRole(String value) {
        try {
            return AppUserRole.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new DataRetrievalFailureException("app_users role 값이 지원 범위를 벗어났습니다.");
        }
    }
}
