package com.portal26.hive.config;

import javax.sql.DataSource;

import com.titaniamlabs.crypto.CryptoEngine;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Custom DataSource wiring — does <b>not</b> use {@code spring.datasource.*} autoconfiguration.
 * <p>
 * Connection credentials ({@code hive.datasource.url|username|password}) are loaded from AWS
 * Secrets Manager in non-local profiles via {@link com.portal26.hive.config.cloud.CloudPropertiesFacade}.
 * Password may be plaintext today or CryptoEngine-encrypted later; decrypt falls back to as-is.
 */
@Configuration
public class DataSourceConfig {

	private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

	@Value("${hive.datasource.url}")
	private String url;

	@Value("${hive.datasource.username}")
	private String username;

	@Value("${hive.datasource.password}")
	private String password;

	@Value("${hive.datasource.driver-class-name:org.postgresql.Driver}")
	private String driverClassName;

	@Value("${hive.datasource.hikari.pool-name:pulse-hive-hikari}")
	private String poolName;

	@Value("${hive.datasource.hikari.maximum-pool-size:15}")
	private int maximumPoolSize;

	@Value("${hive.datasource.hikari.minimum-idle:5}")
	private int minimumIdle;

	@Value("${hive.datasource.hikari.connection-timeout:30000}")
	private long connectionTimeoutMs;

	@Value("${hive.datasource.hikari.idle-timeout:600000}")
	private long idleTimeoutMs;

	@Value("${hive.datasource.hikari.max-lifetime:1800000}")
	private long maxLifetimeMs;

	@Value("${hive.datasource.hikari.leak-detection-threshold:60000}")
	private long leakDetectionThresholdMs;

	@Value("${hive.datasource.hikari.register-mbeans:true}")
	private boolean registerMbeans;

	@Bean
	@Primary
	public DataSource dataSource() {
		HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(url);
		ds.setUsername(username);
		ds.setDriverClassName(driverClassName);
		ds.setPassword(decryptPassword(password));
		ds.setPoolName(poolName);
		ds.setMaximumPoolSize(maximumPoolSize);
		ds.setMinimumIdle(minimumIdle);
		ds.setConnectionTimeout(connectionTimeoutMs);
		ds.setIdleTimeout(idleTimeoutMs);
		ds.setMaxLifetime(maxLifetimeMs);
		ds.setLeakDetectionThreshold(leakDetectionThresholdMs);
		ds.setRegisterMbeans(registerMbeans);
		return ds;
	}

	private String decryptPassword(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		try {
			return CryptoEngine.sysPropertyDecryptWithPassphrase(value);
		}
		catch (Exception e) {
			log.warn("datasource password not encrypted, using as-is");
			return value;
		}
	}
}
