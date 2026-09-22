package com.portal26.hive.msp;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CurrentMspResolver {

	private final UUID seedMspId;

	@Autowired
	public CurrentMspResolver(@Value("${hive.seed-msp-id}") UUID seedMspId) {
		this.seedMspId = seedMspId;
	}

	public UUID currentMspId() {
		return seedMspId;
	}
}
