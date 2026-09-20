package com.portal26.hive.provisioning.dto;

import java.util.List;

/** Wrapper object, not a bare array: the UI reads {@code { "jobs": [...] }}. */
public record JobListResponse(List<JobListItem> jobs) {
}
