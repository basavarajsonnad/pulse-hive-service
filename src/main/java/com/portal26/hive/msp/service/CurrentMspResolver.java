package com.portal26.hive.msp.service;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the MSP that owns the current request.
 *
 * <p>There is no Cognito yet, so this returns the seeded MSP from configuration. When JWT
 * authentication lands, this class is the only one that changes: read the claim here and every
 * caller keeps working. Callers must never read {@code msp_id} from anywhere else, and the API
 * never accepts it from the client.
 */
@Component
public class CurrentMspResolver {

	private final UUID seededMspId;

	@Autowired
	public CurrentMspResolver(@Value("${hive.msp.seed-id}") UUID seededMspId) {
		this.seededMspId = seededMspId;
	}

	public UUID currentMspId() {
		return seededMspId;
	}
}
