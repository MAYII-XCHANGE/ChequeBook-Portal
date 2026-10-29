package com.example.chequebookrequestportal.corebanking.managerscheque;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcManagersChequeAccountLookup implements ManagersChequeAccountLookup {
    private static final String SQL = """
            SELECT FORACID AS account_number,
                   ACCT_NAME AS account_name,
                   ACCT_OWNERSHIP AS account_ownership,
                   SCHM_CODE AS scheme_code,
                   ACCT_CRNCY_CODE AS currency
            FROM TBAADM.GAM
            WHERE FORACID = :configuredAccount
              AND ACCT_OWNERSHIP = 'O'
              AND DEL_FLG = 'N'
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcManagersChequeAccountLookup(@Qualifier("finacleDataSource") DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout(5);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    @Override
    public List<AccountDetails> findConfiguredAccount(String configuredAccount) {
        return jdbc.query(SQL, Map.of("configuredAccount", configuredAccount),
                (result, row) -> new AccountDetails(
                        result.getString("account_number"),
                        result.getString("account_name"),
                        result.getString("account_ownership"),
                        result.getString("scheme_code"),
                        result.getString("currency")));
    }
}
