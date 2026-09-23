package com.portal26.hive.staff.service;

import com.portal26.hive.msp.entity.Msp;
import com.portal26.hive.msp.repository.MspRepository;
import com.portal26.hive.staff.entity.Staff;
import com.portal26.hive.staff.repository.StaffRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Find-or-create staff/MSP in a fresh transaction so concurrent first-login
 * unique-key races can be caught via {@code saveAndFlush} and recovered with a
 * re-read that is not blocked by a rollback-only outer transaction.
 */
@Service
public class LoginIdentityService {

	private static final Logger log = LoggerFactory.getLogger(LoginIdentityService.class);

	private final StaffRepository staffRepository;
	private final MspRepository mspRepository;
	private final UUID seedMspId;
	private final boolean seedMspFallbackEnabled;

	public LoginIdentityService(
			StaffRepository staffRepository,
			MspRepository mspRepository,
			@Value("${hive.seed-msp-id}") UUID seedMspId,
			@Value("${hive.seed-msp-fallback-enabled:false}") boolean seedMspFallbackEnabled) {
		this.staffRepository = staffRepository;
		this.mspRepository = mspRepository;
		this.seedMspId = seedMspId;
		this.seedMspFallbackEnabled = seedMspFallbackEnabled;
	}

	/**
	 * Cognito is the source of identity; Hive only stores a local staff row for FK/session.
	 * First login creates the row; later logins reuse it. No Hive-side role validation.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Staff findOrCreateStaff(String email) {
		return staffRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
			Staff created = new Staff();
			created.setEmail(email);
			created.setRole(null);
			try {
				Staff saved = staffRepository.saveAndFlush(created);
				log.info("Created staff record on first login for {}", email);
				return saved;
			}
			catch (DataIntegrityViolationException ex) {
				return staffRepository.findByEmailIgnoreCase(email).orElseThrow(() -> ex);
			}
		});
	}

	/**
	 * Reads custom:provider from the ID token. If present, find-or-create an MSP row by name.
	 * If absent, fall back to the seeded MSP id only when {@code hive.seed-msp-fallback-enabled}
	 * is true (local/dev). Otherwise fail closed.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public UUID resolveMspId(String provider) {
		if (!StringUtils.hasText(provider)) {
			if (!seedMspFallbackEnabled) {
				throw new IllegalStateException(
						"Cognito custom:provider is required when seed MSP fallback is disabled");
			}
			return seedMspId;
		}
		String name = provider.trim();
		return mspRepository.findByNameIgnoreCase(name)
				.map(Msp::getId)
				.orElseGet(() -> {
					try {
						Msp saved = mspRepository.saveAndFlush(Msp.forProviderCreate(name));
						log.info("Created MSP '{}' from Cognito custom:provider on first login", name);
						return saved.getId();
					}
					catch (DataIntegrityViolationException ex) {
						return mspRepository.findByNameIgnoreCase(name)
								.map(Msp::getId)
								.orElseThrow(() -> ex);
					}
				});
	}
}
