package com.accountflow.config;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Verifies the MongoDB connection once at startup, so a bad URI or credential
 * shows up in the logs instead of surfacing later on the first repository call.
 *
 * <p>Disable with {@code accountflow.mongo.startup-check=false}; the test
 * profile does exactly that so the suite never dials out.
 */
@Component
@ConditionalOnProperty(name = "accountflow.mongo.startup-check", havingValue = "true", matchIfMissing = true)
public class MongoConnectionCheck implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(MongoConnectionCheck.class);

	private final MongoTemplate mongoTemplate;

	public MongoConnectionCheck(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void run(String... args) {
		try {
			Document buildInfo = this.mongoTemplate.executeCommand(new Document("buildInfo", 1));
			log.info("MongoDB connected: database '{}', server version {}", this.mongoTemplate.getDb().getName(),
					buildInfo.getString("version"));
			log.info("Collections: {}", this.mongoTemplate.getCollectionNames());
		}
		catch (Exception ex) {
			log.error("MongoDB connection failed: {}", ex.getMessage());
		}
	}

}
