package com.accountflow.common.idempotency;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IdempotencyRecordRepository extends MongoRepository<IdempotencyRecord, String> {

}
