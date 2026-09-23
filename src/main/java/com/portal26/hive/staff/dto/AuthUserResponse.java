package com.portal26.hive.staff.dto;

import com.portal26.hive.staff.enums.HiveRole;
import java.util.UUID;

public record AuthUserResponse(UUID id, String email, HiveRole role) {
}
