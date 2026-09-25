package com.accountflow.admin.service;

import java.util.Locale;

import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.User;
import com.accountflow.user.domain.UserStatus;
import com.accountflow.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator.
 *
 * <p>Self-registration always produces a USER, and only an administrator can
 * promote anyone - so without this there is no way to get the first one short of
 * editing the database by hand.
 *
 * <p>Runs only when no active administrator exists, so it is safe on every
 * start-up and cannot be used to silently re-promote someone who was demoted.
 * If the address belongs to an existing user, that user is promoted rather than
 * duplicated; their password is left alone.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final UserRepository userRepository;

	private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

	private final String email;

	private final String password;

	public AdminBootstrap(UserRepository userRepository,
			org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
			@Value("${accountflow.admin.bootstrap-email:}") String email,
			@Value("${accountflow.admin.bootstrap-password:}") String password) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.email = email;
		this.password = password;
	}

	@Override
	public void run(org.springframework.boot.ApplicationArguments args) {
		if (this.userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) > 0) {
			return;
		}
		if (this.email.isBlank() || this.password.isBlank()) {
			log.warn("No administrator exists. Set ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD "
					+ "to create one on the next start-up.");
			return;
		}

		String normalized = this.email.trim().toLowerCase(Locale.ROOT);
		User user = this.userRepository.findByEmail(normalized).orElse(null);

		if (user != null) {
			user.setRole(Role.ADMIN);
			user.setStatus(UserStatus.ACTIVE);
			this.userRepository.save(user);
			log.info("Promoted existing user {} to ADMIN", normalized);
			return;
		}

		this.userRepository.save(User.builder()
			.firstName("Administrator")
			.email(normalized)
			.passwordHash(this.passwordEncoder.encode(this.password))
			.role(Role.ADMIN)
			.status(UserStatus.ACTIVE)
			.build());
		log.info("Created the first administrator: {}. Change this password after signing in.", normalized);
	}

}
