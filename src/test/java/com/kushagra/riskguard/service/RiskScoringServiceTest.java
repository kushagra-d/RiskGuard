package com.kushagra.riskguard.service;

import com.kushagra.riskguard.dto.RiskResponse;
import com.kushagra.riskguard.dto.TransactionRequest;
import com.kushagra.riskguard.model.Transaction;
import com.kushagra.riskguard.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskScoringServiceTest {

    @Mock
    private TransactionRepository repository;

    private RiskScoringService service;

    @BeforeEach
    void setUp() {
        service = new RiskScoringService(repository);
        lenient().when(repository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
        lenient().when(repository.findByAccountIdAndTimestampAfter(any(), any())).thenReturn(Collections.emptyList());
    }

    private static TransactionRequest request(String amount, String country) {
        TransactionRequest r = new TransactionRequest();
        r.setAccountId("ACC-1");
        r.setAmount(new BigDecimal(amount));
        r.setMerchantCountry(country);
        return r;
    }

    private static List<Transaction> priorTransactions(int count) {
        return IntStream.range(0, count).mapToObj(i -> new Transaction()).toList();
    }

    @Test
    void lowAmountDomesticFirstTimeScoresZeroAndIsNotFlagged() {
        RiskResponse response = service.score(request("500.00", "IN"));

        assertEquals(0, response.getRiskScore());
        assertFalse(response.isFlagged());
        assertTrue(response.getReasons().isEmpty());
        assertEquals(1L, response.getTransactionId());
    }

    @Test
    void highAmountAloneScoresFortyAndDoesNotCrossFlagThreshold() {
        RiskResponse response = service.score(request("100000.01", "US"));

        assertEquals(40, response.getRiskScore());
        assertFalse(response.isFlagged());
        assertEquals(1, response.getReasons().size());
        assertTrue(response.getReasons().get(0).contains("100000"));
    }

    @Test
    void amountExactlyAtThresholdDoesNotTriggerHighAmountRule() {
        RiskResponse response = service.score(request("100000", "GB"));

        assertEquals(0, response.getRiskScore());
        assertTrue(response.getReasons().isEmpty());
    }

    @Test
    void foreignMerchantAddsTwentyFivePointsWithReason() {
        RiskResponse response = service.score(request("100.00", "NG"));

        assertEquals(25, response.getRiskScore());
        assertFalse(response.isFlagged());
        assertEquals(1, response.getReasons().size());
        assertTrue(response.getReasons().get(0).contains("Foreign merchant"));
        assertTrue(response.getReasons().get(0).contains("NG"));
    }

    @Test
    void countryMatchIsCaseInsensitive() {
        RiskResponse response = service.score(request("100.00", "sg"));

        assertEquals(0, response.getRiskScore());
    }

    @Test
    void highAmountPlusForeignMerchantCrossesFlagThresholdWithBothReasons() {
        RiskResponse response = service.score(request("250000", "RU"));

        assertEquals(65, response.getRiskScore());
        assertTrue(response.isFlagged());
        assertEquals(2, response.getReasons().size());
        assertTrue(response.getReasons().stream().anyMatch(r -> r.contains("High amount")));
        assertTrue(response.getReasons().stream().anyMatch(r -> r.contains("Foreign merchant")));
    }

    @Test
    void velocityRuleTriggersWithThreeOrMorePriorTransactions() {
        when(repository.findByAccountIdAndTimestampAfter(eq("ACC-1"), any(Instant.class)))
                .thenReturn(priorTransactions(3));

        RiskResponse response = service.score(request("100.00", "IN"));

        assertEquals(35, response.getRiskScore());
        assertFalse(response.isFlagged());
        assertEquals(1, response.getReasons().size());
        assertTrue(response.getReasons().get(0).contains("High velocity"));
    }

    @Test
    void velocityRuleDoesNotTriggerWithFewerThanThreePriorTransactions() {
        when(repository.findByAccountIdAndTimestampAfter(eq("ACC-1"), any(Instant.class)))
                .thenReturn(priorTransactions(2));

        RiskResponse response = service.score(request("100.00", "IN"));

        assertEquals(0, response.getRiskScore());
        assertTrue(response.getReasons().isEmpty());
    }

    @Test
    void velocityQueriesTheLastTenMinutes() {
        Instant before = Instant.now();
        service.score(request("100.00", "IN"));

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findByAccountIdAndTimestampAfter(eq("ACC-1"), since.capture());
        Instant expectedEarliest = before.minusSeconds(600);
        Instant expectedLatest = Instant.now().minusSeconds(600);
        assertFalse(since.getValue().isBefore(expectedEarliest));
        assertFalse(since.getValue().isAfter(expectedLatest));
    }

    @Test
    void allRulesCombinedAreCappedAtOneHundred() {
        when(repository.findByAccountIdAndTimestampAfter(eq("ACC-1"), any(Instant.class)))
                .thenReturn(priorTransactions(5));

        RiskResponse response = service.score(request("500000", "CN"));

        assertEquals(100, response.getRiskScore());
        assertTrue(response.isFlagged());
        assertEquals(3, response.getReasons().size());
    }

    @Test
    void persistsTransactionWithScoreFlagAndJoinedReasons() {
        service.score(request("250000", "RU"));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertEquals("ACC-1", saved.getAccountId());
        assertEquals(65, saved.getRiskScore());
        assertTrue(saved.isFlagged());
        assertTrue(saved.getReasons().contains("High amount"));
        assertTrue(saved.getReasons().contains("; "));
        assertTrue(saved.getReasons().contains("Foreign merchant"));
    }
}
