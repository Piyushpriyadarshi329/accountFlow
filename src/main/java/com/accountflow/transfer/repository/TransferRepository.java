package com.accountflow.transfer.repository;

import java.util.Optional;

import com.accountflow.transfer.domain.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TransferRepository extends MongoRepository<Transfer, String> {

	Optional<Transfer> findByIdAndUserId(String id, String userId);

	Page<Transfer> findByUserId(String userId, Pageable pageable);

}
