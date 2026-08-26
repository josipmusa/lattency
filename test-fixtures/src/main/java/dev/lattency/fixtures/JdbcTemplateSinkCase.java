package dev.lattency.fixtures;

import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcTemplateSinkCase {
    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateSinkCase(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int count() {
        return jdbcTemplate.queryForObject("select count(*) from orders", Integer.class);
    }
}
