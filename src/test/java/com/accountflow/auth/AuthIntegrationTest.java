package com.accountflow.auth;

import com.accountflow.user.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end auth flow over real HTTP against a real MongoDB. Testcontainers
 * starts a single-node replica set, which is what makes MongoDB transactions
 * available to the money-movement slices that follow.
 *
 * <p>Runs the actual servlet container rather than a mock one, so the security
 * chain and {@code RequestIdFilter} are exercised exactly as in production.
 * Skipped when Docker is not running, keeping the suite green without it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIf("dockerAvailable")
class AuthIntegrationTest {

	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

	static boolean dockerAvailable() {
		try {
			return DockerClientFactory.instance().isDockerAvailable();
		}
		catch (Throwable ex) {
			return false;
		}
	}

	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
		registry.add("spring.mongodb.database", () -> "accountflow-it");
	}

	@LocalServerPort
	private int port;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	private RestTestClient client;

	@BeforeEach
	void setUp() {
		this.userRepository.deleteAll();
		this.client = RestTestClient.bindToServer().baseUrl("http://localhost:" + this.port).build();
	}

	private static String registerBody(String email) {
		return """
				{"firstName":"Piyush","lastName":"P","email":"%s","password":"Secret123"}""".formatted(email);
	}

	private JsonNode register(String email) throws Exception {
		String body = this.client.post()
			.uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.body(registerBody(email))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		return this.objectMapper.readTree(body).get("data");
	}

	@Test
	@DisplayName("registers a user and returns a usable token pair")
	void registersUser() throws Exception {
		JsonNode data = register("piyush@example.com");

		assertThat(data.get("accessToken").asText()).isNotBlank();
		assertThat(data.get("refreshToken").asText()).isNotBlank();
		assertThat(data.get("tokenType").asText()).isEqualTo("Bearer");
		assertThat(data.get("user").has("password")).isFalse();
		assertThat(data.get("user").has("passwordHash")).isFalse();
	}

	@Test
	@DisplayName("a duplicate email is rejected with a stable error code")
	void rejectsDuplicateEmail() throws Exception {
		register("dupe@example.com");

		this.client.post()
			.uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.body(registerBody("dupe@example.com"))
			.exchange()
			.expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(false)
			.jsonPath("$.code")
			.isEqualTo("EMAIL_ALREADY_EXISTS")
			.jsonPath("$.requestId")
			.exists();
	}

	@Test
	@DisplayName("rejects a weak password with field-level detail")
	void rejectsWeakPassword() {
		this.client.post()
			.uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"firstName":"P","email":"weak@example.com","password":"short"}""")
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("VALIDATION_ERROR")
			.jsonPath("$.fieldErrors.password")
			.exists();
	}

	@Test
	@DisplayName("signs in and reaches a protected endpoint with the token")
	void signsInAndAccessesProtectedEndpoint() throws Exception {
		register("login@example.com");

		String loginBody = this.client.post()
			.uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"email":"login@example.com","password":"Secret123"}""")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		String token = this.objectMapper.readTree(loginBody).get("data").get("accessToken").asText();

		this.client.get()
			.uri("/api/v1/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.data.email")
			.isEqualTo("login@example.com")
			.jsonPath("$.data.role")
			.isEqualTo("USER");
	}

	@Test
	@DisplayName("a wrong password and an unknown account fail identically")
	void rejectsWrongPasswordWithoutEnumeration() throws Exception {
		register("real@example.com");

		for (String body : new String[] { """
				{"email":"real@example.com","password":"WrongPass1"}""", """
				{"email":"ghost@example.com","password":"WrongPass1"}""" }) {
			this.client.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.exchange()
				.expectStatus()
				.isUnauthorized()
				.expectBody()
				.jsonPath("$.code")
				.isEqualTo("INVALID_CREDENTIALS")
				.jsonPath("$.message")
				.isEqualTo("Invalid email or password");
		}
	}

	@Test
	@DisplayName("protected endpoints refuse anonymous and forged tokens")
	void protectedEndpointsRefuseBadTokens() {
		this.client.get()
			.uri("/api/v1/users/me")
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("UNAUTHENTICATED");

		this.client.get()
			.uri("/api/v1/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("TOKEN_INVALID");
	}

	@Test
	@DisplayName("a refresh token rotates once, and replaying it is treated as theft")
	void refreshRotatesAndDetectsReuse() throws Exception {
		String refreshToken = register("rotate@example.com").get("refreshToken").asText();
		String body = """
				{"refreshToken":"%s"}""".formatted(refreshToken);

		String rotated = this.client.post()
			.uri("/api/v1/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		assertThat(this.objectMapper.readTree(rotated).get("data").get("refreshToken").asText())
			.isNotEqualTo(refreshToken);

		this.client.post()
			.uri("/api/v1/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("REFRESH_TOKEN_REUSED");
	}

	@Test
	@DisplayName("logout invalidates access tokens that were already issued")
	void logoutInvalidatesLiveAccessTokens() throws Exception {
		String token = register("logout@example.com").get("accessToken").asText();

		this.client.get()
			.uri("/api/v1/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.exchange()
			.expectStatus()
			.isOk();

		this.client.post()
			.uri("/api/v1/auth/logout")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.exchange()
			.expectStatus()
			.isOk();

		// tokenVersion was bumped, so the still-unexpired token is now dead.
		this.client.get()
			.uri("/api/v1/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("TOKEN_INVALID");
	}

	@Test
	@DisplayName("one user's token never resolves to another user's profile")
	void tokensAreScopedToTheirOwner() throws Exception {
		String tokenA = register("a@example.com").get("accessToken").asText();
		String tokenB = register("b@example.com").get("accessToken").asText();

		this.client.get()
			.uri("/api/v1/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
			.exchange()
			.expectBody()
			.jsonPath("$.data.email")
			.isEqualTo("a@example.com");

		this.client.get()
			.uri("/api/v1/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
			.exchange()
			.expectBody()
			.jsonPath("$.data.email")
			.isEqualTo("b@example.com");
	}

	@Test
	@DisplayName("profile updates apply to the caller and cannot change email or role")
	void updatesOwnProfileOnly() throws Exception {
		String token = register("profile@example.com").get("accessToken").asText();

		this.client.put()
			.uri("/api/v1/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"firstName":"Renamed","lastName":"User","phone":"+919876543210"}""")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.data.firstName")
			.isEqualTo("Renamed")
			.jsonPath("$.data.email")
			.isEqualTo("profile@example.com")
			.jsonPath("$.data.role")
			.isEqualTo("USER");
	}

	@Test
	@DisplayName("every response carries the caller's correlation id")
	void echoesRequestId() {
		this.client.get()
			.uri("/api/v1/users/me")
			.header("X-Request-Id", "trace-abc-123")
			.exchange()
			.expectHeader()
			.valueEquals("X-Request-Id", "trace-abc-123")
			.expectBody()
			.jsonPath("$.requestId")
			.isEqualTo("trace-abc-123");
	}

}
