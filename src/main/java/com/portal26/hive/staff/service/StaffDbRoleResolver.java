package com.portal26.hive.staff.service;

import com.portal26.hive.staff.entity.Staff;
import com.portal26.hive.staff.enums.HiveRole;
import org.springframework.stereotype.Component;

@Component
public class StaffDbRoleResolver implements RoleResolver {

	@Override
	public HiveRole resolve(Staff staff) {
		return staff.getRole();
	}
}
