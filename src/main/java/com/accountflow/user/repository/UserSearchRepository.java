package com.accountflow.user.repository;

import java.util.List;
import java.util.regex.Pattern;

import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.User;
import com.accountflow.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** Admin-facing user search. Not reachable by an ordinary user. */
@Repository
public class UserSearchRepository {

	private final MongoTemplate mongoTemplate;

	public UserSearchRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public Page<User> search(String query, UserStatus status, Role role, Pageable pageable) {
		Query mongoQuery = build(query, status, role).with(pageable);
		List<User> content = this.mongoTemplate.find(mongoQuery, User.class);
		long total = this.mongoTemplate.count(build(query, status, role), User.class);
		return new PageImpl<>(content, pageable, total);
	}

	private Query build(String query, UserStatus status, Role role) {
		Criteria criteria = new Criteria();
		if (status != null) {
			criteria = criteria.and("status").is(status);
		}
		if (role != null) {
			criteria = criteria.and("role").is(role);
		}
		if (query != null && !query.isBlank()) {
			// Quoted: a search box must not be able to inject a regex.
			String safe = Pattern.quote(query.trim());
			criteria = criteria.andOperator(new Criteria().orOperator(Criteria.where("email").regex(safe, "i"),
					Criteria.where("firstName").regex(safe, "i"), Criteria.where("lastName").regex(safe, "i"),
					Criteria.where("phone").regex(safe, "i")));
		}
		return new Query(criteria);
	}

}
