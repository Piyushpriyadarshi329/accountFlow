package com.accountflow.ledger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CyclicBarrier;

import com.accountflow.account.repository.AccountRepository;
import com.accountflow.transaction.repository.TransactionRepository;
import com.accountflow.user.repository.UserRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money-correctness tests against a real MongoDB replica set, which is what
 * makes the conditional atomic update and MongoDB transactions behave as they
 * will in production. Skipped when Docker is unavailable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIf("dockerAvailable")
class LedgerIntegrationTest {

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
		registry.add("spring.mongodb.database", () -> "accountflow-ledger-it");
	}

	@LocalServerPort
	private int port;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	private RestTestClient client;

	private String token;

	@BeforeEach
	void setUp() {
		this.transactionRepository.deleteAll();
		this.accountRepository.deleteAll();
		this.userRepository.deleteAll();
		this.client = RestTestClient.bindToServer().baseUrl("http://localhost:" + this.port).build();
		this.token = registerUser("owner@example.com");
	}

	private String registerUser(String email) {
		String body = this.client.post()
			.uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"firstName":"Owner","email":"%s","password":"Secret123"}""".formatted(email))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		return this.objectMapper.readTree(body).get("data").get("accessToken").asText();
	}

	private String createAccount(String token, String type, String opening, String creditLimit) {
		String body = this.client.post()
			.uri("/api/v1/accounts")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"accountName":"HDFC Savings","bankName":"HDFC","accountType":"%s","currency":"INR",\
					"openingBalance":%s,"creditLimit":%s}""".formatted(type, opening, creditLimit))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		return this.objectMapper.readTree(body).get("data").get("id").asText();
	}

	private RestTestClient.ResponseSpec post(String path, String token, String key, String amount) {
		return this.client.post()
			.uri(path)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"amount":%s,"category":"FOOD","description":"test"}""".formatted(amount))
			.exchange();
	}

	private BigDecimal balanceOf(String accountId) {
		String body = this.client.get()
			.uri("/api/v1/accounts/" + accountId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		return new BigDecimal(this.objectMapper.readTree(body).get("data").get("currentBalance").asString());
	}

	@Test
	@DisplayName("credit raises the balance and records before/after")
	void creditRaisesBalance() {
		String account = createAccount(this.token, "SAVINGS", "10000", "0");

		post("/api/v1/accounts/" + account + "/transactions/credit", this.token, UUID.randomUUID().toString(),
				"5000")
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.data.balanceBefore")
			.isEqualTo("10000.00")
			.jsonPath("$.data.balanceAfter")
			.isEqualTo("15000.00")
			.jsonPath("$.data.direction")
			.isEqualTo("IN");

		assertThat(balanceOf(account)).isEqualByComparingTo("15000.00");
	}

	@Test
	@DisplayName("debit lowers the balance")
	void debitLowersBalance() {
		String account = createAccount(this.token, "SAVINGS", "15000", "0");

		post("/api/v1/accounts/" + account + "/transactions/debit", this.token, UUID.randomUUID().toString(), "3000")
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.data.balanceAfter")
			.isEqualTo("12000.00")
			.jsonPath("$.data.direction")
			.isEqualTo("OUT");

		assertThat(balanceOf(account)).isEqualByComparingTo("12000.00");
	}

	@Test
	@DisplayName("a debit beyond the balance is refused and changes nothing")
	void refusesOverdraft() {
		String account = createAccount(this.token, "SAVINGS", "1000", "0");

		post("/api/v1/accounts/" + account + "/transactions/debit", this.token, UUID.randomUUID().toString(), "5000")
			.expectStatus()
			.isEqualTo(422)
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("INSUFFICIENT_BALANCE");

		assertThat(balanceOf(account)).isEqualByComparingTo("1000.00");
	}

	@Test
	@DisplayName("a credit card may go negative within its limit, but not beyond")
	void creditCardHonoursItsLimit() {
		String card = createAccount(this.token, "CREDIT_CARD", "0", "50000");

		post("/api/v1/accounts/" + card + "/transactions/debit", this.token, UUID.randomUUID().toString(), "20000")
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.data.balanceAfter")
			.isEqualTo("-20000.00");

		post("/api/v1/accounts/" + card + "/transactions/debit", this.token, UUID.randomUUID().toString(), "40000")
			.expectStatus()
			.isEqualTo(422)
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("INSUFFICIENT_BALANCE");

		assertThat(balanceOf(card)).isEqualByComparingTo("-20000.00");
	}

	@Test
	@DisplayName("a closed account refuses new transactions")
	void closedAccountRefusesPostings() {
		String account = createAccount(this.token, "SAVINGS", "0", "0");

		this.client.patch()
			.uri("/api/v1/accounts/" + account + "/status")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"status":"CLOSED"}""")
			.exchange()
			.expectStatus()
			.isOk();

		post("/api/v1/accounts/" + account + "/transactions/credit", this.token, UUID.randomUUID().toString(), "100")
			.expectStatus()
			.isEqualTo(422)
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("ACCOUNT_CLOSED");
	}

	@Test
	@DisplayName("an account holding money cannot be closed")
	void cannotCloseFundedAccount() {
		String account = createAccount(this.token, "SAVINGS", "500", "0");

		this.client.patch()
			.uri("/api/v1/accounts/" + account + "/status")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"status":"CLOSED"}""")
			.exchange()
			.expectStatus()
			.isEqualTo(422)
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("ACCOUNT_NOT_EMPTY");
	}

	@Test
	@DisplayName("two simultaneous debits cannot both succeed against one balance")
	void concurrentDebitsCannotOverdraw() throws Exception {
		// The scenario from the specification: balance 10,000; A debits 8,000 and
		// B debits 7,000 at the same instant. Exactly one must win.
		String account = createAccount(this.token, "SAVINGS", "10000", "0");

		CyclicBarrier startTogether = new CyclicBarrier(2);
		List<Callable<Integer>> attempts = new ArrayList<>();
		for (String amount : new String[] { "8000", "7000" }) {
			attempts.add(() -> {
				startTogether.await();
				return post("/api/v1/accounts/" + account + "/transactions/debit", this.token,
						UUID.randomUUID().toString(), amount)
					.returnResult()
					.getStatus()
					.value();
			});
		}

		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Integer> statuses = new ArrayList<>();
		try {
			for (Future<Integer> future : pool.invokeAll(attempts)) {
				statuses.add(future.get());
			}
		}
		finally {
			pool.shutdownNow();
		}

		// Exactly one wins. The loser is refused for insufficient funds, or told
		// to retry if contention outlasted the retry budget - never both posted.
		assertThat(statuses).filteredOn((status) -> status == 201).hasSize(1);
		assertThat(statuses).filteredOn((status) -> status == 422 || status == 409).hasSize(1);

		BigDecimal finalBalance = balanceOf(account);
		assertThat(finalBalance).isIn(new BigDecimal("2000.00"), new BigDecimal("3000.00"));
		assertThat(finalBalance.signum()).isNotNegative();
	}

	@Test
	@DisplayName("under heavy contention the balance still reconciles exactly and never goes negative")
	void manyConcurrentDebitsNeverGoNegative() throws Exception {
		// 20 threads each debiting 1,000 against a balance of 10,000.
		//
		// The assertion is the invariant, not a fixed count: at most ten can
		// succeed, and whatever number does must account for the balance to the
		// paisa. A request that loses the race is refused (422) or asked to retry
		// (409) - both are correct, and which one depends on timing.
		String account = createAccount(this.token, "SAVINGS", "10000", "0");

		int threads = 20;
		CyclicBarrier startTogether = new CyclicBarrier(threads);
		List<Callable<Integer>> attempts = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			attempts.add(() -> {
				startTogether.await();
				return post("/api/v1/accounts/" + account + "/transactions/debit", this.token,
						UUID.randomUUID().toString(), "1000")
					.returnResult()
					.getStatus()
					.value();
			});
		}

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		long accepted = 0;
		List<Integer> statuses = new ArrayList<>();
		try {
			for (Future<Integer> future : pool.invokeAll(attempts)) {
				int status = future.get();
				statuses.add(status);
				if (status == 201) {
					accepted++;
				}
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(accepted).isBetween(1L, 10L);
		assertThat(statuses).allMatch((status) -> status == 201 || status == 422 || status == 409);

		BigDecimal expected = new BigDecimal("10000.00").subtract(new BigDecimal(accepted * 1000).setScale(2));
		assertThat(balanceOf(account)).isEqualByComparingTo(expected);
		assertThat(balanceOf(account).signum()).isNotNegative();
	}

	@Test
	@DisplayName("replaying the same Idempotency-Key returns the original and moves no money")
	void idempotentRetryDoesNotDoubleCharge() {
		String account = createAccount(this.token, "SAVINGS", "10000", "0");
		String key = UUID.randomUUID().toString();
		String path = "/api/v1/accounts/" + account + "/transactions/debit";

		String first = post(path, this.token, key, "2500").expectStatus()
			.isCreated()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		String firstReference = this.objectMapper.readTree(first).get("data").get("transactionReference").asString();

		String replayed = post(path, this.token, key, "2500").expectBody(String.class)
			.returnResult()
			.getResponseBody();
		JsonNode replayedData = this.objectMapper.readTree(replayed).get("data");

		assertThat(replayedData.get("transactionReference").asString()).isEqualTo(firstReference);
		assertThat(balanceOf(account)).isEqualByComparingTo("7500.00");
		assertThat(this.transactionRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("reusing a key with a different body is refused rather than served the old result")
	void rejectsKeyReuseWithDifferentBody() {
		String account = createAccount(this.token, "SAVINGS", "10000", "0");
		String key = UUID.randomUUID().toString();
		String path = "/api/v1/accounts/" + account + "/transactions/debit";

		post(path, this.token, key, "2500").expectStatus().isCreated();
		post(path, this.token, key, "9999").expectStatus()
			.isEqualTo(422)
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("IDEMPOTENCY_KEY_REUSED");

		assertThat(balanceOf(account)).isEqualByComparingTo("7500.00");
	}

	@Test
	@DisplayName("a money-moving request without an Idempotency-Key is refused")
	void requiresIdempotencyKey() {
		String account = createAccount(this.token, "SAVINGS", "10000", "0");

		this.client.post()
			.uri("/api/v1/accounts/" + account + "/transactions/debit")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"amount":100}""")
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
	}

	@Test
	@DisplayName("another user's account is invisible, for reads and for postings alike")
	void cannotTouchAnotherUsersAccount() {
		String victimAccount = createAccount(this.token, "SAVINGS", "10000", "0");
		String attackerToken = registerUser("attacker@example.com");

		this.client.get()
			.uri("/api/v1/accounts/" + victimAccount)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + attackerToken)
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("ACCOUNT_NOT_FOUND");

		post("/api/v1/accounts/" + victimAccount + "/transactions/debit", attackerToken,
				UUID.randomUUID().toString(), "5000")
			.expectStatus()
			.isNotFound();

		assertThat(balanceOf(victimAccount)).isEqualByComparingTo("10000.00");
	}

	@Test
	@DisplayName("an over-precise amount is refused rather than rounded")
	void refusesOverPreciseAmount() {
		String account = createAccount(this.token, "SAVINGS", "10000", "0");

		post("/api/v1/accounts/" + account + "/transactions/credit", this.token, UUID.randomUUID().toString(),
				"10.999")
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("INVALID_AMOUNT");

		assertThat(balanceOf(account)).isEqualByComparingTo("10000.00");
	}

	@Test
	@DisplayName("the ledger reconciles: opening balance plus every signed posting equals the balance")
	void ledgerReconcilesWithTheBalance() {
		String account = createAccount(this.token, "SAVINGS", "10000", "0");
		String path = "/api/v1/accounts/" + account + "/transactions/";

		post(path + "credit", this.token, UUID.randomUUID().toString(), "5000").expectStatus().isCreated();
		post(path + "debit", this.token, UUID.randomUUID().toString(), "2000").expectStatus().isCreated();
		post(path + "credit", this.token, UUID.randomUUID().toString(), "750.50").expectStatus().isCreated();
		post(path + "debit", this.token, UUID.randomUUID().toString(), "1250.25").expectStatus().isCreated();

		BigDecimal replayed = this.transactionRepository.findAll()
			.stream()
			.map((t) -> t.getDirection().signed(t.getAmount()))
			.reduce(new BigDecimal("10000.00"), BigDecimal::add);

		assertThat(replayed).isEqualByComparingTo(balanceOf(account)).isEqualByComparingTo("12500.25");
	}

}
