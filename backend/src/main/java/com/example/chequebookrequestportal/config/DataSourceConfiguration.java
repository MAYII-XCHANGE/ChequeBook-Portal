package com.example.chequebookrequestportal.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class DataSourceConfiguration {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties applicationDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource applicationDataSource(
            @Qualifier("applicationDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("finacle.spring.datasource")
    DataSourceProperties finacleDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "finacleDataSource")
    @ConfigurationProperties("finacle.spring.datasource.hikari")
    HikariDataSource finacleDataSource(
            @Qualifier("finacleDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName("finacle-cheque-eligibility");
        dataSource.setReadOnly(true);
        dataSource.setMinimumIdle(0);
        dataSource.setMaximumPoolSize(5);
        dataSource.setConnectionTimeout(3000);
        dataSource.setInitializationFailTimeout(-1);
        return dataSource;
    }
}
