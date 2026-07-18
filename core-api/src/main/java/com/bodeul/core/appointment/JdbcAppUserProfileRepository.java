package com.bodeul.core.appointment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
class JdbcAppUserProfileRepository implements AppUserProfileRepository {

    private static final String SELECT_COLUMNS = """
            select id, role, name, email, phone
            from bodeul.app_users
            """;

    private final JdbcTemplate jdbcTemplate;

    JdbcAppUserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AppUserProfile> findById(UUID userId) {
        return jdbcTemplate.query(
                        SELECT_COLUMNS + "where id = ? limit 1",
                        (resultSet, rowNumber) -> mapProfile(resultSet),
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public List<AppUserProfile> findByEmail(AppUserRole role, String email) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + "where role = ? and lower(email) = ? order by id limit 2",
                (resultSet, rowNumber) -> mapProfile(resultSet),
                role.name(),
                email);
    }

    @Override
    public List<AppUserProfile> findByPhone(AppUserRole role, String phone) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + "where role = ? and phone = ? order by id limit 2",
                (resultSet, rowNumber) -> mapProfile(resultSet),
                role.name(),
                phone);
    }

    private AppUserProfile mapProfile(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new AppUserProfile(
                resultSet.getObject("id", UUID.class),
                AppUserRole.valueOf(resultSet.getString("role")),
                resultSet.getString("name"),
                resultSet.getString("email"),
                resultSet.getString("phone"));
    }
}
