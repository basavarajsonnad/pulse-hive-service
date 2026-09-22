package ai.portal26.hive.msp;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MspRlsSession {

	private final EntityManager entityManager;

	@Autowired
	public MspRlsSession(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public void apply(UUID mspId) {
		entityManager
				.createNativeQuery("SELECT set_config('app.current_msp', :mspId, true)", String.class)
				.setParameter("mspId", mspId.toString())
				.getSingleResult();
	}
}
