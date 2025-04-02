package org.example.wallet.controller;
import jakarta.validation.Valid;
import org.example.wallet.DTO.WalletBalanceResponse;
import org.example.wallet.DTO.WalletOperationRequest;
import org.example.wallet.entity.Wallet;
import org.example.wallet.service.WalletService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class WalletController {
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/wallet")
    public ResponseEntity<Void> performOperation(@Valid @RequestBody WalletOperationRequest request) {
        walletService.performOperation(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/wallets/{walletId}")
    public ResponseEntity<WalletBalanceResponse> getWalletBalance(@PathVariable UUID walletId) {
        BigDecimal balance = walletService.getBalance(walletId);
        WalletBalanceResponse response = new WalletBalanceResponse(walletId, balance);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/wallets")
    public ResponseEntity<List<UUID>> getAllWalletIds() {
        List<UUID> walletIds = walletService.getAllWalletIds();
        return ResponseEntity.ok(walletIds);
    }

    @PostMapping("/wallets")
    public ResponseEntity<WalletBalanceResponse> createWallet() {
        Wallet wallet = walletService.createWallet();
        return ResponseEntity.status(201).body(new WalletBalanceResponse(wallet.getId(), wallet.getBalance()));
    }

}
