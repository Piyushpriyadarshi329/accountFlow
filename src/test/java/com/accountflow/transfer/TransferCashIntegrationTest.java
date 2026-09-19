package com.accountflow.transfer;

import java.math.BigDecimal;
import java.util.UUID;

import com.accountflow.account.repository.AccountRepository;
import com.accountflow.transaction.repository.TransactionRepository;
import com.accountflow.transfer.repository.TransferRepository;
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
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Transfers and cash against a real replica set, where MongoDB transactions apply. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIf("dockerAvailable")
class TransferCashIntegrationTest {

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
		registry.add("spring.mongodb.database", () -> "accountflow-transfer-it");
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

	@Autowired
	private TransferRepository transferRepository;

	private RestTestClient client;

	private String token;

	@BeforeEach
	void setUp() {
		this.transferRepository.deleteAll();
		this.transactionRepository.deleteAll();
		this.accountRepository.deleteAll();
		this.userRepository.deleteAll();
		this.client = RestTestClient.bindToServer().baseUrl("http://localhost:" + this.port).build();
		this.token = register("owner@example.com");
	}

	private String register(String email) {
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
		return this.objectMapper.readTree(body).get("data").get("accessToken").asString();
	}

	private String account(String token, String name, String currency, String opening) {
		String body = this.client.post()
			.uri("/api/v1/accounts")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"accountName":"%s","accountType":"SAVINGS","currency":"%s","openingBalance":%s}"""
				.formatted(name, currency, opening))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		return this.objectMapper.readTree(body).get("data").get("id").asString();
	}

	private BigDecimal balance(String accountId) {
		String body = this.client.get()
			.uri("/api/v1/accounts/" + accountId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.exchange()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		return new BigDecimal(this.objectMapper.readTree(body).get("data").get("currentBalance").asString());
	}

	private BigDecimal cashBalance() {
		String body = this.client.get()
			.uri("/api/v1/cash")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		return new BigDecimal(this.objectMapper.readTree(body).get("data").get("balance").asString());
	}

	private RestTestClient.ResponseSpec transfer(String from, String to, String amount, String key) {
		return this.client.post()
			.uri("/api/v1/transfers")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"sourceAccountId":"%s","destinationAccountId":"%s","amount":%s,"description":"Monthly transfer"}"""
				.formatted(from, to, amount))
			.exchange();
	}

	@Test
	@DisplayName("transferring 10,000 from HDFC to SBI moves exactly that, both ways")
	void transferMovesMoneyBetweenAccounts() {
		String hdfc = account(this.token, "HDFC Savings", "INR", "50000");
		String sbi = account(this.token, "SBI Savings", "INR", "20000");

		transfer(hdfc, sbi, "10000", UUID.randomUUID().toString()).expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.data.amount")
			.isEqualTo("10000.00")
			.jsonPath("$.data.status")
			.isEqualTo("COMPLETED")
			.jsonPath("$.data.kind")
			.isEqualTo("BANK_TO_BANK");

		assertThat(balance(hdfc)).isEqualByComparingTo("40000.00");
		assertThat(balance(sbi)).isEqualByComparingTo("30000.00");
	}

	@Test
	@DisplayName("a transfer writes one linked leg into each account's history")
	void transferWritesBothLegs() {
		String hdfc = account(this.token, "HDFC Savings", "INR", "50000");
		String sbi = account(this.token, "SBI Savings", "INR", "20000");

		String body = transfer(hdfc, sbi, "10000", UUID.randomUUID().toString()).expectBody(String.class)
			.returnResult()
			.getResponseBody();
		String reference = this.objectMapper.readTree(body).get("data").get("transferReference").asString();

		assertThat(this.transactionRepository.findAll()).hasSize(2)
			.allSatisfy((txn) -> assertThat(txn.getTransferReference()).isEqualTo(reference));
		assertThat(this.transactionRepository.findAll())
			.extracting((txn) -> txn.getTransactionType().name())
			.containsExactlyInAnyOrder("TRANSFER_OUT", "TRANSFER_IN");
	}

	@Test
	@DisplayName("a transfer the source cannot fund leaves both balances untouched")
	void failedTransferIsAtomic() {
		String hdfc = account(this.token, "HDFC Savings", "INR", "1000");
		String sbi = account(this.token, "SBI Savings", "INR", "20000");

		transfer(hdfc, sbi, "5000", UUID.randomUUID().toString()).expectStatus()
			.isEqualTo(422)
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("INSUFFICIENT_BALANCE");

		// The credit leg must have rolled back with the debit.
		assertThat(balance(hdfc)).isEqualByComparingTo("1000.00");
		assertThat(balance(sbi)).isEqualByComparingTo("20000.00");
		assertThat(this.transactionRepository.count()).isZero();
		assertThat(this.transferRepository.count()).isZero();
	}

	@Test
	@DisplayName("withdrawing cash moves money from the bank into the cash wallet")
	void bankToCash() {
		String hdfc = account(this.token, "HDFC Savings", "INR", "50000");

		this.client.post()
			.uri("/api/v1/transfers/bank-to-cash")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"accountId":"%s","amount":10000,"description":"ATM withdrawal"}""".formatted(hdfc))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.data.kind")
			.isEqualTo("BANK_TO_CASH");

		assertThat(balance(hdfc)).isEqualByComparingTo("40000.00");
		assertThat(cashBalance()).isEqualByComparingTo("10000.00");
	}

	@Test
	@DisplayName("depositing cash moves money from the cash wallet into the bank")
	void cashToBank() {
		String hdfc = account(this.token, "HDFC Savings", "INR", "50000");
		String sbi = account(this.token, "SBI Savings", "INR", "20000");

		// Get 10,000 into cash first.
		this.client.post()
			.uri("/api/v1/transfers/bank-to-cash")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"accountId":"%s","amount":10000}""".formatted(hdfc))
			.exchange()
			.expectStatus()
			.isCreated();

		this.client.post()
			.uri("/api/v1/transfers/cash-to-bank")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"accountId":"%s","amount":4000,"description":"Cash deposit"}""".formatted(sbi))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.data.kind")
			.isEqualTo("CASH_TO_BANK");

		assertThat(cashBalance()).isEqualByComparingTo("6000.00");
		assertThat(balance(sbi)).isEqualByComparingTo("24000.00");
	}

	@Test
	@DisplayName("cash deposit and withdrawal adjust the cash balance")
	void cashDepositAndWithdrawal() {
		this.client.post()
			.uri("/api/v1/cash/deposit")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"amount":5000,"description":"Cash received"}""")
			.exchange()
			.expectStatus()
			.isCreated();
		assertThat(cashBalance()).isEqualByComparingTo("5000.00");

		this.client.post()
			.uri("/api/v1/cash/withdraw")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"amount":1500,"description":"Groceries"}""")
			.exchange()
			.expectStatus()
			.isCreated();
		assertThat(cashBalance()).isEqualByComparingTo("3500.00");
	}

	@Test
	@DisplayName("cash withdrawal beyond the cash balance is refused")
	void cashWithdrawalCannotOverdraw() {
		this.client.post()
			.uri("/api/v1/cash/deposit")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"amount":2000}""")
			.exchange()
			.expectStatus()
			.isCreated();

		this.client.post()
			.uri("/api/v1/cash/withdraw")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"amount":5000}""")
			.exchange()
			.expectStatus()
			.isEqualTo(422)
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("INSUFFICIENT_BALANCE");

		assertThat(cashBalance()).isEqualByComparingTo("2000.00");
	}

	@Test
	@DisplayName("transferring to the same account is refused")
	void refusesSelfTransfer() {
		String hdfc = account(this.token, "HDFC Savings", "INR", "50000");

		transfer(hdfc, hdfc, "1000", UUID.randomUUID().toString()).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("SAME_ACCOUNT_TRANSFER");
	}

	@Test
	@DisplayName("transferring across currencies is refused")
	void refusesCrossCurrencyTransfer() {
		String inr = account(this.token, "HDFC Savings", "INR", "50000");
		String usd = account(this.token, "Citi USD", "USD", "1000");

		transfer(inr, usd, "1000", UUID.randomUUID().toString()).expectStatus()
			.isEqualTo(422)
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("CURRENCY_MISMATCH");

		assertThat(balance(inr)).isEqualByComparingTo("50000.00");
	}

	@Test
	@DisplayName("a transfer into another user's account is refused")
	void refusesTransferToForeignAccount() {
		String mine = account(this.token, "HDFC Savings", "INR", "50000");
		String otherToken = register("other@example.com");
		String theirs = account(otherToken, "Their Savings", "INR", "0");

		transfer(mine, theirs, "1000", UUID.randomUUID().toString()).expectStatus()
			.isNotFound()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("ACCOUNT_NOT_FOUND");

		assertThat(balance(mine)).isEqualByComparingTo("50000.00");
	}

	@Test
	@DisplayName("replaying a transfer key returns the original and moves no further money")
	void transferIsIdempotent() {
		String hdfc = account(this.token, "HDFC Savings", "INR", "50000");
		String sbi = account(this.token, "SBI Savings", "INR", "20000");
		String key = UUID.randomUUID().toString();

		String first = transfer(hdfc, sbi, "10000", key).expectStatus()
			.isCreated()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		String reference = this.objectMapper.readTree(first).get("data").get("transferReference").asString();

		String replay = transfer(hdfc, sbi, "10000", key).expectBody(String.class).returnResult().getResponseBody();

		assertThat(this.objectMapper.readTree(replay).get("data").get("transferReference").asString())
			.isEqualTo(reference);
		assertThat(balance(hdfc)).isEqualByComparingTo("40000.00");
		assertThat(balance(sbi)).isEqualByComparingTo("30000.00");
		assertThat(this.transferRepository.count()).isEqualTo(1);
		assertThat(this.transactionRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("bank and cash together conserve money across a round trip")
	void bankAndCashConserveMoney() {
		String hdfc = account(this.token, "HDFC Savings", "INR", "50000");
		BigDecimal before = balance(hdfc).add(cashBalance());

		for (String amount : new String[] { "10000", "2500" }) {
			this.client.post()
				.uri("/api/v1/transfers/bank-to-cash")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"accountId":"%s","amount":%s}""".formatted(hdfc, amount))
				.exchange()
				.expectStatus()
				.isCreated();
		}
		this.client.post()
			.uri("/api/v1/transfers/cash-to-bank")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"accountId":"%s","amount":3000}""".formatted(hdfc))
			.exchange()
			.expectStatus()
			.isCreated();

		// Moving money between one's own accounts must not create or destroy any.
		assertThat(balance(hdfc).add(cashBalance())).isEqualByComparingTo(before);
	}

}
