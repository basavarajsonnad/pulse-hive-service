package com.portal26.hive.staff.principal;

import com.portal26.hive.session.HiveSession;
import com.portal26.hive.staff.enums.HiveRole;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class HivePrincipal implements UserDetails {

	private final UUID staffId;
	private final String email;
	private final HiveRole role;

	public HivePrincipal(HiveSession session) {
		this.staffId = session.staffId();
		this.email = session.email();
		this.role = session.role();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (role == null) {
			return List.of();
		}
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return null;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
