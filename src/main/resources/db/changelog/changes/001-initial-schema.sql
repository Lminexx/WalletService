
CREATE TABLE wallets (
                         id UUID PRIMARY KEY,
                         balance NUMERIC(19, 2) NOT NULL CHECK (balance >= 0)

);

CREATE INDEX idx_wallets_balance ON wallets(balance);