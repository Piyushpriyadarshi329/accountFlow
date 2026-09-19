package com.accountflow.common.health;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A plain, unauthenticated health endpoint for load balancers and uptime checks.
 *
 * <p>It pings MongoDB rather than returning a constant, because a service that
 * reports healthy while its database is unreachable is worse than no health
 * check at all - traffic keeps being routed to an instance that can only fail.
 *
 * <p>Nothing about the database is echoed back: a health endpoint is public, and
 * a hostname or driver message in the body is free reconnaissance. The detail
 * goes to the logs instead.
 */
@RestController
@Tag(name = "Health", description = "Liveness for load balancers and uptime checks")
public class HealthController {

	private static final Logger log = LoggerFactory.getLogger(HealthController.class);

	private final MongoTemplate mongoTemplate;

	private final String applicationName;

	public HealthController(MongoTemplate mongoTemplate,
			@Value("${spring.application.name:AccountFlow}") String applicationName) {
		this.mongoTemplate = mongoTemplate;
		this.applicationName = applicationName;
	}

	@GetMapping("/health")
	@SecurityRequirements
	@Operation(summary = "Service health",
			description = "200 with status UP when the database responds, 503 with status DOWN when it does not.")
	public ResponseEntity<Map<String, Object>> health() {
		boolean databaseUp = pingDatabase();

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", databaseUp ? "UP" : "DOWN");
		body.put("application", this.applicationName);
		body.put("timestamp", Instant.now().toString());

		return databaseUp ? ResponseEntity.ok(body)
				: ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
	}

	private boolean pingDatabase() {
		try {
			this.mongoTemplate.executeCommand(new Document("ping", 1));
			return true;
		}
		catch (Exception ex) {
			log.warn("Health check failed: database did not respond ({})", ex.getMessage());
			return false;
		}
	}

}
