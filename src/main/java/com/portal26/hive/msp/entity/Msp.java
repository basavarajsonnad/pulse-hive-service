package com.portal26.hive.msp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "msp")
@Getter
@Setter
@NoArgsConstructor
public class Msp {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "name", nullable = false, length = 255)
	private String name;

	/** Hive host prefix, e.g. {@code cinchit-hive} for cinchit-hive.portal26.ai. */
	@Column(name = "subdomain", nullable = false, length = 63)
	private String subdomain;

	/** MSP lifecycle (active/disabled), unrelated to tenant provisioning status. */
	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;
}
