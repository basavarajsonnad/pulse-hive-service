package ai.portal26.hive.staff.dto;

import ai.portal26.hive.staff.enums.HiveRole;
import java.util.UUID;

public record AuthUserResponse(UUID id, String email, HiveRole role) {
}
