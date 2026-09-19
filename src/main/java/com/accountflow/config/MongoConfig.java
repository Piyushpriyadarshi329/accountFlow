package com.accountflow.config;

import com.accountflow.security.AuthenticatedUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableMongoAuditing(auditorAwareRef = "auditorAware")
public class MongoConfig {

	/**
	 * Without this bean {@code @Transactional} is silently a no-op on MongoDB —
	 * the annotation resolves to no transaction manager and every multi-document
	 * write loses its atomicity guarantee with no error.
	 */
	@Bean
	MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
		return new MongoTransactionManager(databaseFactory);
	}

	@Bean
	AuditorAware<String> auditorAware() {
		return () -> {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication == null || !authentication.isAuthenticated()) {
				return Optional.of("SYSTEM");
			}
			if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
				return Optional.of(user.userId());
			}
			return Optional.of("SYSTEM");
		};
	}

}
