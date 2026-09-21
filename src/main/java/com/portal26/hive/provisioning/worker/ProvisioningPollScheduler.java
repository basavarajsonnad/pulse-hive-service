package com.portal26.hive.provisioning.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hive.provisioning.poll.enabled", havingValue = "true", matchIfMissing = true)
public class ProvisioningPollScheduler {

	private static final Logger log = LoggerFactory.getLogger(ProvisioningPollScheduler.class);

	private final ProvisioningPollService pollService;

	@Autowired
	public ProvisioningPollScheduler(ProvisioningPollService pollService) {
		this.pollService = pollService;
	}

	@Scheduled(cron = "${hive.provisioning.poll.cron:0/30 * * * * *}")
	public void tick() {
		try {
			pollService.pollRunningJobs();
		} catch (RuntimeException ex) {
			log.error("Provisioning poll tick failed", ex);
		}
	}
}
