package com.accountflow.auth.repository;

import java.util.Optional;

import com.accountflow.auth.domain.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	java.util.List<RefreshToken> findByFamilyAndRevokedFalse(String family);

	java.util.List<RefreshToken> findByUserIdAndRevokedFalse(String userId);

}
