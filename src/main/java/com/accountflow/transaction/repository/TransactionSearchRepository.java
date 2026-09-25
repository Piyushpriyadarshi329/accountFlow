package com.accountflow.transaction.repository;

import java.util.List;
import java.util.stream.Stream;

import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.dto.TransactionFilter;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Filtered access to the ledger.
 *
 * <p>Every query starts from {@code userId}, which is both the authorization
 * boundary and the leading field of every index on the collection - so a filter
 * narrows an already-selective index rather than scanning.
 */
@Repository
public class TransactionSearchRepository {

	private final MongoTemplate mongoTemplate;

	public TransactionSearchRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public Page<Transaction> search(String userId, TransactionFilter filter, Pageable pageable) {
		Query query = buildQuery(userId, filter).with(pageable);
		List<Transaction> content = this.mongoTemplate.find(query, Transaction.class);
		long total = this.mongoTemplate.count(buildQuery(userId, filter), Transaction.class);
		return new PageImpl<>(content, pageable, total);
	}

	/**
	 * Streams matches for export. A cursor rather than a list: an export has no
	 * page size, and a year of transactions loaded into a List is how an export
	 * endpoint takes the service down.
	 *
	 * <p>The caller must close the stream.
	 */
	public Stream<Transaction> stream(String userId, TransactionFilter filter) {
		Query query = buildQuery(userId, filter)
			.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
					"transactionDate"));
		query.cursorBatchSize(500);
		return this.mongoTemplate.stream(query, Transaction.class);
	}

	/**
	 * The most recent posting on an account strictly before an instant - the
	 * balance the account stood at going into a statement period.
	 *
	 * <p>Ordered by {@code postedAt}, not {@code transactionDate}: the running
	 * balance chain follows the order things were actually applied, and
	 * transactionDate is a business date the caller may backdate.
	 */
	public java.util.Optional<Transaction> lastPostingBefore(String userId, String accountId,
			java.time.Instant instant) {
		Query query = new Query(Criteria.where("userId")
			.is(userId)
			.and("accountId")
			.is(accountId)
			.and("postedAt")
			.lt(instant)).with(org.springframework.data.domain.Sort
				.by(org.springframework.data.domain.Sort.Direction.DESC, "postedAt"))
			.limit(1);
		return java.util.Optional.ofNullable(this.mongoTemplate.findOne(query, Transaction.class));
	}

	/** Postings on one account within a period, oldest first, for a statement. */
	public List<Transaction> postingsBetween(String userId, String accountId, java.time.Instant fromInclusive,
			java.time.Instant toExclusive, int limit) {
		Criteria criteria = Criteria.where("userId").is(userId).and("accountId").is(accountId);
		Criteria posted = Criteria.where("postedAt");
		if (fromInclusive != null) {
			posted = posted.gte(fromInclusive);
		}
		if (toExclusive != null) {
			posted = posted.lt(toExclusive);
		}
		Query query = new Query(criteria.andOperator(posted))
			.with(org.springframework.data.domain.Sort
				.by(org.springframework.data.domain.Sort.Direction.ASC, "postedAt"))
			.limit(limit);
		return this.mongoTemplate.find(query, Transaction.class);
	}

	public long count(String userId, TransactionFilter filter) {
		return this.mongoTemplate.count(buildQuery(userId, filter), Transaction.class);
	}

	private Query buildQuery(String userId, TransactionFilter filter) {
		Criteria criteria = Criteria.where("userId").is(userId);

		if (filter.accountId() != null) {
			criteria = criteria.and("accountId").is(filter.accountId());
		}
		if (filter.transactionType() != null) {
			criteria = criteria.and("transactionType").is(filter.transactionType());
		}
		if (filter.category() != null) {
			criteria = criteria.and("category").is(filter.category());
		}
		if (filter.merchant() != null) {
			criteria = criteria.and("merchant").regex(quote(filter.merchant()), "i");
		}
		if (filter.query() != null) {
			criteria = criteria.and("description").regex(quote(filter.query()), "i");
		}

		if (filter.fromInstant() != null || filter.toInstantExclusive() != null) {
			Criteria date = Criteria.where("transactionDate");
			if (filter.fromInstant() != null) {
				date = date.gte(filter.fromInstant());
			}
			if (filter.toInstantExclusive() != null) {
				date = date.lt(filter.toInstantExclusive());
			}
			criteria = criteria.andOperator(date);
		}
		if (filter.minAmount() != null || filter.maxAmount() != null) {
			Criteria amount = Criteria.where("amount");
			if (filter.minAmount() != null) {
				amount = amount.gte(new Decimal128(filter.minAmount()));
			}
			if (filter.maxAmount() != null) {
				amount = amount.lte(new Decimal128(filter.maxAmount()));
			}
			criteria = criteria.andOperator(amount);
		}
		return new Query(criteria);
	}

	/** User input goes into a regex, so it must not be able to act as one. */
	private static String quote(String value) {
		return java.util.regex.Pattern.quote(value.trim());
	}

}
