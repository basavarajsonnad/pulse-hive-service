package ai.portal26.hive.staff.repository;

import ai.portal26.hive.staff.entity.Staff;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, UUID> {

	Optional<Staff> findByEmailIgnoreCase(String email);
}
