package com.portal26.hive.provisioning.dto;

/**
 * The only error shape these APIs return. Mirrors Core's envelope and the UI's ApiError type.
 *
 * <p>Permitted values for {@code code} are in {@link com.portal26.hive.core.exception.ErrorCodes}.
 */
public record ApiErrorResponse(String code, String message) {
}
