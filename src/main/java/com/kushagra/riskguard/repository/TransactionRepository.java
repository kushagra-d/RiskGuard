package com.kushagra.riskguard.repository;

import com.kushagra.riskguard.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountIdAndTimestampAfter(String accountId, Instant since);
}
