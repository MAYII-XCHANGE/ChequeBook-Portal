package com.example.chequebookrequestportal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.example.chequebookrequestportal.corebanking.eligibility.ChequeEligibilityLookup;
import com.example.chequebookrequestportal.corebanking.eligibility.JdbcChequeEligibilityLookup;
import com.example.chequebookrequestportal.corebanking.managerscheque.JdbcManagersChequeAccountLookup;
import com.example.chequebookrequestportal.corebanking.managerscheque.ManagersChequeAccountLookup;
import com.example.chequebookrequestportal.corebanking.managerscheque.ManagersChequeAccountLookup.AccountDetails;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DataSourceConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DataSourceConfiguration.class)
            .withBean(JdbcChequeEligibilityLookup.class)
            .withBean(JdbcManagersChequeAccountLookup.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:application;DB_CLOSE_DELAY=-1",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "finacle.spring.datasource.url=jdbc:h2:mem:finacle;DB_CLOSE_DELAY=-1",
                    "finacle.spring.datasource.username=sa",
                    "finacle.spring.datasource.password=",
                    "finacle.spring.datasource.driver-class-name=org.h2.Driver",
                    "finacle.spring.datasource.hikari.read-only=false");

    @Test
    void chequeEligibilityLookupUsesTheFinacleDataSource() {
        contextRunner.run(context -> {
            assertFalse(context.getStartupFailure() != null,
                    () -> "Application context failed: " + context.getStartupFailure());

            DataSource applicationDataSource = context.getBean("dataSource", DataSource.class);
            DataSource finacleDataSource = context.getBean("finacleDataSource", DataSource.class);
            assertNotSame(applicationDataSource, finacleDataSource);
            assertSame(applicationDataSource, context.getBean(DataSource.class));

            try (Connection connection = finacleDataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA TBAADM");
                statement.execute("CREATE TABLE TBAADM.GAM (foracid VARCHAR(20), chq_alwd_flg VARCHAR(1), "
                        + "acct_name VARCHAR(100), acct_ownership VARCHAR(5), schm_code VARCHAR(20), "
                        + "sol_id VARCHAR(20), acct_crncy_code VARCHAR(3), del_flg VARCHAR(1))");
                statement.execute("INSERT INTO TBAADM.GAM VALUES "
                        + "('1003000321', 'Y', 'Manager Cheque', 'O', '2004', '001', 'NGN', 'N')");
            }

            ChequeEligibilityLookup lookup = context.getBean(ChequeEligibilityLookup.class);
            assertEquals(new ChequeEligibilityLookup.Result(true, "Y"),
                    lookup.findChequeAllowedFlag("1003000321"));

            ManagersChequeAccountLookup managersChequeLookup = context.getBean(ManagersChequeAccountLookup.class);
            assertEquals(List.of(new AccountDetails(
                    "1003000321", "Manager Cheque", "O", "2004", "NGN")),
                    managersChequeLookup.findConfiguredAccount("1003000321"));
        });
    }
}
