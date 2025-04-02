package org.example.wallet;
import org.example.wallet.DTO.WalletOperationRequest;
import org.example.wallet.entity.Wallet;
import org.example.wallet.enums.OperationType;
import org.example.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;


import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class WalletControllerTest {


    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres:16-alpine");


    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    private Wallet testWallet;
    private final BigDecimal initialBalance = new BigDecimal("100.50");

    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }


    @BeforeEach
    void setUp() {
        testWallet = new Wallet();
        testWallet.setBalance(initialBalance);
        testWallet = walletRepository.save(testWallet);
    }

    @Test
    void getWalletBalance_Success() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/{walletId}", testWallet.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.walletId", is(testWallet.getId().toString())))
                .andExpect(jsonPath("$.balance", comparesEqualTo(initialBalance.doubleValue())));
    }

    @Test
    void getWalletBalance_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/wallets/{walletId}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Wallet not found with id: " + nonExistentId)));
    }

    @Test
    void performOperation_Deposit_Success() throws Exception {
        BigDecimal depositAmount = new BigDecimal("50.25");
        WalletOperationRequest request = new WalletOperationRequest();
        request.setWalletId(testWallet.getId());
        request.setOperationType(OperationType.DEPOSIT);
        request.setAmount(depositAmount);

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Wallet updatedWallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertEquals(0, initialBalance.add(depositAmount).compareTo(updatedWallet.getBalance())); // Сравнение BigDecimal
    }

    @Test
    void performOperation_Withdraw_Success() throws Exception {
        BigDecimal withdrawAmount = new BigDecimal("30.00");
        WalletOperationRequest request = new WalletOperationRequest();
        request.setWalletId(testWallet.getId());
        request.setOperationType(OperationType.WITHDRAW);
        request.setAmount(withdrawAmount);

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Wallet updatedWallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertEquals(0, initialBalance.subtract(withdrawAmount).compareTo(updatedWallet.getBalance()));
    }

    @Test
    void performOperation_Withdraw_InsufficientFunds() throws Exception {
        BigDecimal withdrawAmount = initialBalance.add(BigDecimal.ONE);
        WalletOperationRequest request = new WalletOperationRequest();
        request.setWalletId(testWallet.getId());
        request.setOperationType(OperationType.WITHDRAW);
        request.setAmount(withdrawAmount);

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()) // Ожидаем 400
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Insufficient funds")));

        Wallet notUpdatedWallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertEquals(0, initialBalance.compareTo(notUpdatedWallet.getBalance()));
    }

    @Test
    void performOperation_WalletNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        WalletOperationRequest request = new WalletOperationRequest();
        request.setWalletId(nonExistentId);
        request.setOperationType(OperationType.DEPOSIT);
        request.setAmount(new BigDecimal("10"));

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", containsString("Wallet not found with id: " + nonExistentId)));
    }

    @Test
    void performOperation_InvalidAmount_Negative() throws Exception {
        WalletOperationRequest request = new WalletOperationRequest();
        request.setWalletId(testWallet.getId());
        request.setOperationType(OperationType.DEPOSIT);
        request.setAmount(new BigDecimal("-100"));

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Amount must be positive"))); // Проверяем сообщение валидации
    }

    @Test
    void performOperation_InvalidRequest_MissingAmount() throws Exception {
        String jsonRequest = String.format("{\"walletId\":\"%s\",\"operationType\":\"DEPOSIT\"}", testWallet.getId());

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Amount cannot be null")));
    }

    @Test
    void performOperation_InvalidRequest_MalformedJson() throws Exception {
        String malformedJson = "{\"walletId\":\"" + testWallet.getId() + "\",\"operationType\":\"DEPOSIT\", amount: 100 }";

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Malformed JSON request")));
    }

    @Test
    void createWallet_Success() throws Exception {
        mockMvc.perform(post("/api/v1/wallets"))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.walletId", is(notNullValue())))
                .andExpect(jsonPath("$.balance", comparesEqualTo(0)));

    }

    @Test
    void performOperation_ConcurrentWithdraw_ShouldHandleCorrectly() throws Exception {
        BigDecimal withdrawAmount = new BigDecimal("60.00");

        WalletOperationRequest request1 = new WalletOperationRequest();
        request1.setWalletId(testWallet.getId());
        request1.setOperationType(OperationType.WITHDRAW);
        request1.setAmount(withdrawAmount);
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        Wallet walletAfterFirst = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("40.50").compareTo(walletAfterFirst.getBalance()));

        WalletOperationRequest request2 = new WalletOperationRequest();
        request2.setWalletId(testWallet.getId());
        request2.setOperationType(OperationType.WITHDRAW);
        request2.setAmount(withdrawAmount);

        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient funds")));

        Wallet walletAfterSecond = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("40.50").compareTo(walletAfterSecond.getBalance()));

    }
}