package com.portal26.hive.cognito;

public class CognitoAuthenticationException extends RuntimeException {

	public CognitoAuthenticationException(String message) {
		super(message);
	}

	public CognitoAuthenticationException(String message, Throwable cause) {
		super(message, cause);
	}
}
