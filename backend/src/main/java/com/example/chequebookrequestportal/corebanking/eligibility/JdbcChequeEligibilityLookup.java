package com.example.chequebookrequestportal.corebanking.eligibility;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcChequeEligibilityLookup implements ChequeEligibilityLookup {
    private static final String SQL = """
            SELECT chq_alwd_flg
            FROM TBAADM.GAM
            WHERE foracid = :accountNumber
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcChequeEligibilityLookup(@Qualifier("finacleDataSource") DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout(5);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    @Override
    public Result findChequeAllowedFlag(String accountNumber) {
        try {
            return new Result(true,
                    jdbc.queryForObject(SQL, Map.of("accountNumber", accountNumber), String.class));
        }
        catch (EmptyResultDataAccessException exception) {
            return new Result(false, null);
        }
    }
}
