package com.portal26.hive.staff.service;

import com.portal26.hive.staff.entity.Staff;
import com.portal26.hive.staff.enums.HiveRole;

/**
 * Resolves Hive roles at login/refresh. Staff password login uses DB;
 * SAML can add another implementation later without changing session/filter.
 */
public interface RoleResolver {

	HiveRole resolve(Staff staff);
}
