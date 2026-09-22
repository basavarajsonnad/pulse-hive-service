package ai.portal26.hive.exception;

public class DuplicateCustomerException extends RuntimeException {

	public DuplicateCustomerException(String customerName) {
		super("Customer already exists: " + customerName);
	}
}
