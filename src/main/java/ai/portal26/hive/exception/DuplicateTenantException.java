package ai.portal26.hive.exception;

public class DuplicateTenantException extends RuntimeException {

	public DuplicateTenantException(String tenantName) {
		super("Tenant name already exists: " + tenantName);
	}
}
