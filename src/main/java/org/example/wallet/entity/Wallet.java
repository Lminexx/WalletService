package org.example.wallet.entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Data
@NoArgsConstructor
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    @Column(nullable = false,precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    public Wallet(UUID id) {
        this.id = id;
        this.balance = BigDecimal.ZERO;
    }

    public void deposit(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.balance = this.balance.add(amount);
        }
    }

    public boolean canWithdraw(BigDecimal amount) {
        return amount != null &&
                amount.compareTo(BigDecimal.ZERO) > 0 &&
                this.balance.compareTo(amount) >= 0;
    }

    public void withdraw(BigDecimal amount) {
        if (!canWithdraw(amount)) {
            throw new IllegalArgumentException("Cannot withdraw amount larger than balance.");
        }
        this.balance = this.balance.subtract(amount);
    }

}
