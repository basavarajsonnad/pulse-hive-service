package com.portal26.hive.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HiveSessionRepository extends JpaRepository<HiveSessionEntity, String> {

	@Modifying
	@Query("delete from HiveSessionEntity s where s.expiresAt < :now")
	int deleteExpired(@Param("now") java.time.Instant now);
}
