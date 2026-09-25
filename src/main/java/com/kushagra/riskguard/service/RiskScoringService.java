package com.kushagra.riskguard.service;

import com.kushagra.riskguard.dto.RiskResponse;
import com.kushagra.riskguard.dto.TransactionRequest;
import com.kushagra.riskguard.model.Transaction;
import com.kushagra.riskguard.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class RiskScoringService {

    static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("100000");
    static final int HIGH_AMOUNT_POINTS = 40;

    static final Set<String> TRUSTED_COUNTRIES = Set.of("IN", "US", "GB", "SG", "AE");
    static final int FOREIGN_MERCHANT_POINTS = 25;

    static final Duration VELOCITY_WINDOW = Duration.ofMinutes(10);
    static final int VELOCITY_MIN_PRIOR_TRANSACTIONS = 3;
    static final int VELOCITY_POINTS = 35;

    static final int MAX_SCORE = 100;
    static final int FLAG_THRESHOLD = 50;

    private final TransactionRepository repository;

    public RiskScoringService(TransactionRepository repository) {
        this.repository = repository;
    }

    public RiskResponse score(TransactionRequest request) {
        Instant now = Instant.now();
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (request.getAmount().compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
            score += HIGH_AMOUNT_POINTS;
            reasons.add("High amount: " + request.getAmount().toPlainString()
                    + " exceeds the " + HIGH_AMOUNT_THRESHOLD.toPlainString() + " threshold (+"
                    + HIGH_AMOUNT_POINTS + ")");
        }

        String country = request.getMerchantCountry().trim().toUpperCase();
        if (!TRUSTED_COUNTRIES.contains(country)) {
            score += FOREIGN_MERCHANT_POINTS;
            reasons.add("Foreign merchant: country " + country + " is not in the trusted set "
                    + "[AE, GB, IN, SG, US] (+" + FOREIGN_MERCHANT_POINTS + ")");
        }

        Instant since = now.minus(VELOCITY_WINDOW);
        int recentCount = repository.findByAccountIdAndTimestampAfter(request.getAccountId(), since).size();
        if (recentCount >= VELOCITY_MIN_PRIOR_TRANSACTIONS) {
            score += VELOCITY_POINTS;
            reasons.add("High velocity: " + recentCount + " transactions from account "
                    + request.getAccountId() + " in the last " + VELOCITY_WINDOW.toMinutes()
                    + " minutes (+" + VELOCITY_POINTS + ")");
        }

        score = Math.min(score, MAX_SCORE);
        boolean flagged = score >= FLAG_THRESHOLD;

        Transaction saved = repository.save(new Transaction(
                request.getAccountId(), request.getAmount(), country, now,
                score, flagged, String.join("; ", reasons)));

        return new RiskResponse(saved.getId(), score, flagged, reasons);
    }
}
