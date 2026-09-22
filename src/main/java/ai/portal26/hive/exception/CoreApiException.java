package ai.portal26.hive.exception;

public class CoreApiException extends RuntimeException {

	private final String code;

	public CoreApiException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
