package com.portal26.hive.provisioning.exception;

import com.portal26.hive.core.exception.CoreApiException;
import com.portal26.hive.core.exception.ErrorCodes;
import com.portal26.hive.provisioning.controller.TenantController;
import com.portal26.hive.provisioning.dto.ApiErrorResponse;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/** Scoped to {@link TenantController} so it cannot change error shapes elsewhere in the service. */
@RestControllerAdvice(assignableTypes = TenantController.class)
public class TenantExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(TenantExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> onInvalidBody(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getDefaultMessage() == null
						? error.getField() + " is invalid"
						: error.getDefaultMessage())
				.distinct()
				.collect(Collectors.joining("; "));
		return badRequest(message.isEmpty() ? "Request validation failed" : message);
	}

	/**
	 * Also covers unknown fields: {@code fail-on-unknown-properties} is enabled to match Core, which
	 * surfaces here rather than as a binding error.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> onUnreadableBody(HttpMessageNotReadableException exception) {
		log.debug("Rejected request body", exception);
		return badRequest("Request body is not valid JSON, or contains unknown fields");
	}

	@ExceptionHandler(JobNotFoundException.class)
	ResponseEntity<ApiErrorResponse> onJobNotFound(JobNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiErrorResponse(ErrorCodes.JOB_NOT_FOUND, exception.getMessage()));
	}

	/** Core's code is passed through unchanged so the UI sees Core's own reason. */
	@ExceptionHandler(CoreApiException.class)
	ResponseEntity<ApiErrorResponse> onCoreFailure(CoreApiException exception) {
		HttpStatus status = ErrorCodes.JOB_NOT_FOUND.equals(exception.getCode())
				? HttpStatus.NOT_FOUND
				: HttpStatus.BAD_REQUEST;
		return ResponseEntity.status(status)
				.body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
	}

	/** Core unreachable, or a timeout. Nothing was stored, so the caller may simply retry. */
	@ExceptionHandler(RestClientException.class)
	ResponseEntity<ApiErrorResponse> onCoreUnreachable(RestClientException exception) {
		log.error("Core call failed", exception);
		return badRequest("Unable to reach Portal26 Core");
	}

	private ResponseEntity<ApiErrorResponse> badRequest(String message) {
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(ErrorCodes.VALIDATION_FAILED, message));
	}
}
