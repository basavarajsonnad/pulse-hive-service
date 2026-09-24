package com.portal26.hive.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Custom DataSource wiring — does <b>not</b> use {@code spring.datasource.*} autoconfiguration.
 * <p>
 * Connection credentials ({@code hive.datasource.url|username|password}) are loaded from AWS
 * Secrets Manager in non-local profiles via {@link com.portal26.hive.config.cloud.CloudPropertiesFacade}.
 * Hikari pool settings bind under {@code hive.datasource.hikari.*}.
 */
@Configuration
public class DataSourceConfig {

	@Bean
	@Primary
	@ConfigurationProperties("hive.datasource")
	public DataSourceProperties dataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@Primary
	@ConfigurationProperties("hive.datasource.hikari")
	public DataSource dataSource(DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
	}
}
