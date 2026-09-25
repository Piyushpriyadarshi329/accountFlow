package com.accountflow.user.repository;

import java.util.Optional;

import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.User;
import com.accountflow.user.domain.UserStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByPhone(String phone);

	/** Used to refuse an action that would leave the system with no admin. */
	long countByRoleAndStatus(Role role, UserStatus status);

}
