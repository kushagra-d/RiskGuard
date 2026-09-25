package com.kushagra.riskguard.controller;

import com.kushagra.riskguard.dto.RiskResponse;
import com.kushagra.riskguard.dto.TransactionRequest;
import com.kushagra.riskguard.exception.TransactionNotFoundException;
import com.kushagra.riskguard.model.Transaction;
import com.kushagra.riskguard.repository.TransactionRepository;
import com.kushagra.riskguard.service.RiskScoringService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final RiskScoringService scoringService;
    private final TransactionRepository repository;

    public TransactionController(RiskScoringService scoringService, TransactionRepository repository) {
        this.scoringService = scoringService;
        this.repository = repository;
    }

    @PostMapping("/score")
    public ResponseEntity<RiskResponse> score(@Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(scoringService.score(request));
    }

    @GetMapping("/{id}")
    public Transaction getById(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
