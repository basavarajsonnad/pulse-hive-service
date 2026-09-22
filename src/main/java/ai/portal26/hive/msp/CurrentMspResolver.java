package ai.portal26.hive.msp;

import ai.portal26.hive.staff.principal.HivePrincipal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentMspResolver {

	private final UUID seedMspId;

	@Autowired
	public CurrentMspResolver(@Value("${hive.seed-msp-id}") UUID seedMspId) {
		this.seedMspId = seedMspId;
	}

	public UUID currentMspId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null
				&& authentication.getPrincipal() instanceof HivePrincipal principal
				&& principal.getMspId() != null) {
			return principal.getMspId();
		}
		return seedMspId;
	}
}
