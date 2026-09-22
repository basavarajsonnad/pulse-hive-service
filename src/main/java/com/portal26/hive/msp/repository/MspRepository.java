package com.portal26.hive.msp.repository;

import com.portal26.hive.msp.entity.Msp;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MspRepository extends JpaRepository<Msp, UUID> {
}
