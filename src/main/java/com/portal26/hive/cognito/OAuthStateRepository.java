package com.portal26.hive.cognito;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthStateRepository extends JpaRepository<OAuthStateEntity, String> {
}
