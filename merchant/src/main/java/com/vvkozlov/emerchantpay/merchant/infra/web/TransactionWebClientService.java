package com.vvkozlov.emerchantpay.merchant.infra.web;

import com.vvkozlov.emerchantpay.merchant.service.contract.TransactionMsClient;
import com.vvkozlov.emerchantpay.merchant.service.util.OperationResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class TransactionWebClientService implements TransactionMsClient {

    private final WebClient.Builder webClientBuilder;

    public TransactionWebClientService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public OperationResult<Boolean> getDoesMerchantHaveTransactions(String merchantId) {
        WebClient webClient = webClientBuilder.build();
        try {
            Boolean hasTransactions = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/transaction/checkByMerchant/{merchantId}")
                            .build(merchantId))
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block(); //We should avoid blocking and be asynchronous in real application

            return OperationResult.success(hasTransactions != null && hasTransactions);
        } catch (Exception e) {
            return OperationResult.failure(e.getMessage());
        }
    }
}
