package com.accountflow.account.repository;

import java.util.List;
import java.util.Optional;

import com.accountflow.account.domain.Account;
import com.accountflow.account.domain.AccountType;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Every lookup is scoped by {@code userId}. There is deliberately no
 * {@code findById(String)} in use anywhere: ownership is enforced by the query
 * itself, so it cannot be forgotten at a call site.
 */
public interface AccountRepository extends MongoRepository<Account, String> {

	Optional<Account> findByIdAndUserId(String id, String userId);

	List<Account> findByUserIdAndSystemCashWalletFalse(String userId);

	List<Account> findByUserId(String userId);

	Optional<Account> findByUserIdAndAccountType(String userId, AccountType accountType);

	boolean existsByUserIdAndAccountNumber(String userId, String accountNumber);

	long countByUserId(String userId);

	long countByUserIdAndSystemCashWalletFalse(String userId);

}
