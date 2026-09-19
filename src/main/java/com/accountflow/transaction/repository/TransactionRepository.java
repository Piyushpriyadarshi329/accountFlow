package com.accountflow.transaction.repository;

import java.util.Optional;

import com.accountflow.transaction.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TransactionRepository extends MongoRepository<Transaction, String> {

	Optional<Transaction> findByIdAndUserId(String id, String userId);

	Page<Transaction> findByUserIdAndAccountId(String userId, String accountId, Pageable pageable);

	Page<Transaction> findByUserId(String userId, Pageable pageable);

	Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

}
