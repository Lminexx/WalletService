package org.example.wallet.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;
@Data
@AllArgsConstructor
public class WalletBalanceResponse {
    private UUID walletId;
    private BigDecimal balance;
}
