package com.portal26.hive.msp;

import com.portal26.hive.staff.principal.HivePrincipal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentMspResolver {

	private final UUID seedMspId;
	private final boolean seedMspFallbackEnabled;

	@Autowired
	public CurrentMspResolver(
			@Value("${hive.seed-msp-id}") UUID seedMspId,
			@Value("${hive.seed-msp-fallback-enabled:false}") boolean seedMspFallbackEnabled) {
		this.seedMspId = seedMspId;
		this.seedMspFallbackEnabled = seedMspFallbackEnabled;
	}

	public UUID currentMspId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null
				&& authentication.getPrincipal() instanceof HivePrincipal principal
				&& principal.getMspId() != null) {
			return principal.getMspId();
		}
		if (seedMspFallbackEnabled) {
			return seedMspId;
		}
		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MSP not resolved");
	}
}
