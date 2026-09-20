package com.portal26.hive.core.exception;

import lombok.Getter;

/** A 4xx from Core, carrying Core's own error code so it can be passed through unchanged. */
@Getter
public class CoreApiException extends RuntimeException {

	private final String code;

	public CoreApiException(String code, String message) {
		super(message);
		this.code = code;
	}
}
