package com.portal26.hive.msp.repository;

import com.portal26.hive.msp.entity.Msp;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MspRepository extends JpaRepository<Msp, UUID> {
}
