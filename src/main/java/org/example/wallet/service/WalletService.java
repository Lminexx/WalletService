package org.example.wallet.service;
import org.hibernate.exception.LockAcquisitionException;
import org.springframework.cache.annotation.Cacheable;
import lombok.extern.slf4j.Slf4j;
import org.example.wallet.enums.OperationType;
import org.example.wallet.DTO.WalletOperationRequest;
import org.example.wallet.entity.Wallet;
import org.example.wallet.exception.InsufficientFundsException;
import org.example.wallet.exception.WalletNotFoundException;
import org.example.wallet.repository.WalletRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class WalletService {
    private final WalletRepository walletRepository;
    public static final String WALLET_CACHE_NAME = "walletBalanceCache";

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Cacheable(value = WALLET_CACHE_NAME, key = "#walletId")
    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID walletId) {
        log.info("Getting balance for wallet ID: {}", walletId);
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException("Wallet not found with id: " + walletId);
        }
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found with id: " + walletId));
        return wallet.getBalance();
    }

    @CacheEvict(value = WALLET_CACHE_NAME, key = "#request.walletId")
    @Transactional
    @Retryable(
            include = {
                    LockAcquisitionException.class,
                    JpaSystemException.class,
                    TransientDataAccessException.class
            },
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2)
    )
    public void performOperation(WalletOperationRequest request) {
        UUID walletId = request.getWalletId();
        BigDecimal amount = request.getAmount();
        OperationType operationType = request.getOperationType();
        log.info("Performing {} operation for wallet ID: {} with amount: {}", operationType, walletId, amount);

        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException("Wallet not found with id: " + walletId);
        }

        int rowsAffected;

        switch (operationType) {
            case DEPOSIT:
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Deposit amount must be positive.");
                }
                rowsAffected = walletRepository.depositAmount(walletId, amount);
                if (rowsAffected == 0) {
                    log.error("Deposit failed unexpectedly for existing wallet {}", walletId);
                    throw new RuntimeException("Deposit failed unexpectedly for wallet " + walletId);
                }
                log.info("Deposit operation attempted for wallet {}", walletId);
                break;
            case WITHDRAW:
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Withdrawal amount must be positive.");
                }
                rowsAffected = walletRepository.withdrawAmount(walletId, amount);
                if (rowsAffected == 0) {
                    log.warn("Insufficient funds for withdrawal from wallet {} or wallet disappeared.", walletId);
                    throw new InsufficientFundsException(
                            "Insufficient funds in wallet " + walletId + " for requested amount " + amount
                    );
                }
                log.info("Withdrawal operation attempted for wallet {}", walletId);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation type: " + operationType);
        }
        log.info("Operation successful for wallet {}", walletId);
    }

    @Transactional
    public Wallet createWallet(UUID id) {
        if (walletRepository.existsById(id)) {
            throw new IllegalArgumentException(id + " already exists.");
        }
        Wallet wallet = new Wallet(id);
        return walletRepository.save(wallet);
    }

    @Transactional
    public Wallet createWallet() {
        Wallet wallet = new Wallet();
        return walletRepository.save(wallet);
    }

    @Transactional(readOnly = true)
    public List<UUID> getAllWalletIds() {
        return walletRepository.findAllWalletIds();
    }
}