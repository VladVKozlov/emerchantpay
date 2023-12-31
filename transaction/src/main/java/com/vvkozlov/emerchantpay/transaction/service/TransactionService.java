package com.vvkozlov.emerchantpay.transaction.service;

import com.vvkozlov.emerchantpay.transaction.domain.entities.AbstractTransaction;
import com.vvkozlov.emerchantpay.transaction.infra.repository.TransactionRepository;
import com.vvkozlov.emerchantpay.transaction.service.contract.service.MerchantTransactionsRetrievalService;
import com.vvkozlov.emerchantpay.transaction.service.contract.service.TransactionHandlingService;
import com.vvkozlov.emerchantpay.transaction.service.contract.service.TransactionRetrievalService;
import com.vvkozlov.emerchantpay.transaction.service.mapper.TransactionMapper;
import com.vvkozlov.emerchantpay.transaction.service.model.AbstractTransactionCreateDTO;
import com.vvkozlov.emerchantpay.transaction.service.model.TransactionViewDTO;
import com.vvkozlov.emerchantpay.transaction.service.processor.TransactionProcessor;
import com.vvkozlov.emerchantpay.transaction.service.processor.TransactionProcessorFactory;
import com.vvkozlov.emerchantpay.transaction.service.util.OperationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Transaction service to get, list, import, update, remove transactions.
 */
@Service
public class TransactionService implements MerchantTransactionsRetrievalService,
        TransactionHandlingService, TransactionRetrievalService {
    private final TransactionRepository transactionRepository;
    private final TransactionProcessorFactory processorFactory;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository, TransactionProcessorFactory processorFactory) {
        this.transactionRepository = transactionRepository;
        this.processorFactory = processorFactory;
    }

    @Transactional(readOnly = true)
    public OperationResult<TransactionViewDTO> getTransaction(final String belongsTo, final UUID uuid) {
        AbstractTransaction transaction;
        if (belongsTo != null) {
            transaction = transactionRepository.findByUuidAndBelongsTo(uuid, belongsTo).orElse(null);
        } else {
            transaction = transactionRepository.findById(uuid).orElse(null);
        }

        if (transaction == null) {
            return OperationResult.failure("Transaction not found");
        }
        return OperationResult.success(TransactionMapper.INSTANCE.toDto(transaction));
    }


    @Transactional(readOnly = true)
    public OperationResult<Page<TransactionViewDTO>> getTransactions(final String belongsTo, final Pageable pageable) {
        Page<AbstractTransaction> transactions;
        if (belongsTo != null) {
            transactions = transactionRepository.findAllByBelongsTo(belongsTo, pageable);
        } else {
            transactions = transactionRepository.findAll(pageable);
        }

        List<TransactionViewDTO> viewDTOs = transactions.stream()
                .map(TransactionMapper.INSTANCE::toDto)
                .collect(Collectors.toList());
        return OperationResult.success(new PageImpl<>(viewDTOs, pageable, transactions.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public boolean transactionsExistForMerchant(final String merchantId) {
        return transactionRepository.countByBelongsTo(merchantId) > 0;
    }

    @Transactional
    public OperationResult<TransactionViewDTO> processTransaction(final AbstractTransactionCreateDTO createDto) {
        @SuppressWarnings("unchecked") //this is a fast workaround - avoid in real application
        TransactionProcessor<AbstractTransactionCreateDTO> processor = (TransactionProcessor<AbstractTransactionCreateDTO>) processorFactory.getProcessor(createDto);
        if (processor == null) {
            throw new IllegalArgumentException("Unknown transaction type");
        }
        return processor.process(createDto);
    }
}