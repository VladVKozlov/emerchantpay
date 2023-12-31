package com.vvkozlov.emerchantpay.merchant.service;

import com.vvkozlov.emerchantpay.merchant.domain.constants.MerchantStatusEnum;
import com.vvkozlov.emerchantpay.merchant.domain.constants.UserRoles;
import com.vvkozlov.emerchantpay.merchant.domain.entities.Merchant;
import com.vvkozlov.emerchantpay.merchant.infra.repository.MerchantRepository;
import com.vvkozlov.emerchantpay.merchant.service.contract.OAuthServerAdminClient;
import com.vvkozlov.emerchantpay.merchant.service.contract.TransactionMsClient;
import com.vvkozlov.emerchantpay.merchant.service.contract.mb.MessageBrokerProducer;
import com.vvkozlov.emerchantpay.merchant.service.contract.service.MerchantManagementService;
import com.vvkozlov.emerchantpay.merchant.service.contract.service.MerchantMessageBrokerService;
import com.vvkozlov.emerchantpay.merchant.service.contract.service.MerchantRetrievalService;
import com.vvkozlov.emerchantpay.merchant.service.contract.service.UserCsvImporterService;
import com.vvkozlov.emerchantpay.merchant.service.mapper.MerchantMapper;
import com.vvkozlov.emerchantpay.merchant.service.model.BaseUserViewDTO;
import com.vvkozlov.emerchantpay.merchant.service.model.MerchantEditDTO;
import com.vvkozlov.emerchantpay.merchant.service.model.MerchantViewDTO;
import com.vvkozlov.emerchantpay.merchant.service.model.messagebroker.TransactionMbModel;
import com.vvkozlov.emerchantpay.merchant.service.util.OperationResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The Merchant service to get, list, import, update, remove merchants.
 */
@Service
@Primary
public class MerchantService implements MerchantRetrievalService,
        MerchantManagementService, MerchantMessageBrokerService, UserCsvImporterService {
    private final MerchantRepository merchantRepository;
    private final OAuthServerAdminClient oAuthServerAdminClient;
    private final MessageBrokerProducer mbProducer;
    private final TransactionMsClient transactionMsClient;

    @Autowired
    public MerchantService(MerchantRepository merchantRepository, OAuthServerAdminClient oAuthServerAdminClient, MessageBrokerProducer mbProducer, TransactionMsClient transactionMsClient) {
        this.merchantRepository = merchantRepository;
        this.oAuthServerAdminClient = oAuthServerAdminClient;
        this.mbProducer = mbProducer;
        this.transactionMsClient = transactionMsClient;
    }

    /**
     * Gets merchant.
     *
     * @param id id of merchant to retrieve
     * @return Operation result. If successful, it will contain the merchant dto.
     */
    @Transactional(readOnly = true)
    @Override
    public OperationResult<MerchantViewDTO> getMerchant(final String id) {
        Optional<Merchant> merchantOpt = merchantRepository.findByAuthId(id);
        if (merchantOpt.isPresent()) {
            Merchant merchant = merchantOpt.get();
            MerchantViewDTO viewDTO = MerchantMapper.INSTANCE.toDto(merchant);
            return OperationResult.success(viewDTO);
        } else {
            return OperationResult.failure("Merchant not found");
        }
    }

    /**
     * Gets pageable list of merchants.
     *
     * @param pageable the pageable
     * @return Operation result. If successful, it will contain the merchants page.
     */
    @Transactional(readOnly = true)
    @Override
    public OperationResult<Page<MerchantViewDTO>> getMerchants(final Pageable pageable) {
        Page<Merchant> merchants = merchantRepository.findAll(pageable);
        List<MerchantViewDTO> viewDTOs = merchants.stream()
                .map(MerchantMapper.INSTANCE::toDto)
                .collect(Collectors.toList());
        return OperationResult.success(new PageImpl<>(viewDTOs, pageable, merchants.getTotalElements()));
    }

    /**
     * Update a merchant
     *
     * @param id  the id of record being updated
     * @param dto the merchant edit dto
     * @return the operation result containing updated Merchant DTO
     */
    @Transactional
    @Override
    public OperationResult<MerchantViewDTO> updateMerchant(final String id, final MerchantEditDTO dto) {
        try {
            Optional<Merchant> existingMerchant = merchantRepository.findByAuthId(id);

            if (existingMerchant.isPresent()) {
                Merchant merchantToUpdate = existingMerchant.get();
                Merchant updatedMerchant = MerchantMapper.INSTANCE.toEntity(dto);
                boolean isStatusChanged = !merchantToUpdate.getStatus().equals(updatedMerchant.getStatus());

                merchantToUpdate.setName(updatedMerchant.getName());
                merchantToUpdate.setDescription(updatedMerchant.getDescription());
                merchantToUpdate.setEmail(updatedMerchant.getEmail());
                merchantToUpdate.setStatus(updatedMerchant.getStatus());

                Merchant savedMerchant = merchantRepository.save(merchantToUpdate);

                if (isStatusChanged) {
                    boolean isActive = savedMerchant.getStatus() == MerchantStatusEnum.ACTIVE;
                    mbProducer.sendMessage(savedMerchant.getAuthId(), isActive);
                }

                MerchantViewDTO viewDTO = MerchantMapper.INSTANCE.toDto(savedMerchant);
                return OperationResult.success(viewDTO);
            } else {
                return OperationResult.failure("Merchant not found");
            }
        } catch (Exception e) {
            return OperationResult.failure("An error occurred while updating the merchant: " + e.getMessage());
        }
    }

    /*
    *
    * */
    @Transactional
    @Override
    public OperationResult<MerchantViewDTO> addTransactionAmountToMerchant(TransactionMbModel tranMbModel) {
        try {
            Optional<Merchant> existingMerchant = merchantRepository.findByAuthId(tranMbModel.getBelongsTo());

            if (existingMerchant.isPresent()) {
                Merchant merchantToUpdate = existingMerchant.get();
                BigDecimal currentSum = merchantToUpdate.getTotalTransactionSum();
                if (currentSum == null) {
                    currentSum = BigDecimal.valueOf(0);
                }
                BigDecimal newSum = currentSum.add(tranMbModel.getAmount());
                merchantToUpdate.setTotalTransactionSum(newSum);

                Merchant savedMerchant = merchantRepository.save(merchantToUpdate);

                MerchantViewDTO viewDTO = MerchantMapper.INSTANCE.toDto(savedMerchant);
                return OperationResult.success(viewDTO);
            } else {
                return OperationResult.failure("Merchant not found");
            }
        } catch (Exception e) {
            return OperationResult.failure("An error occurred while updating the merchant's transaction sum: " + e.getMessage());
        }
    }

    /**
     * Import merchants from fake csv.
     *
     * @return the operation result
     */
    @Transactional
    @Override
    public OperationResult<List<? extends BaseUserViewDTO>> importUsersFromCsv() {
        try {
            ClassPathResource resource = new ClassPathResource("csv/merchants.csv");
            Reader reader = new InputStreamReader(resource.getInputStream());

            var format = CSVFormat.Builder.create()
                    .setHeader("name", "description", "email", "status")
                    .setSkipHeaderRecord(true)
                    .setDelimiter(',')
                    .build();

            CSVParser csvParser = new CSVParser(reader, format);
            List<MerchantViewDTO> merchantsImported = new ArrayList<>();
            List<String> merchantRoles = List.of(UserRoles.ROLE_MERCHANT);

            for (CSVRecord record : csvParser) {
                Merchant merchant = new Merchant();
                merchant.setName(record.get("name"));
                merchant.setDescription(record.get("description"));
                merchant.setEmail(record.get("email"));
                merchant.setStatus(MerchantStatusEnum.valueOf(record.get("status")));
                merchant.setTotalTransactionSum(BigDecimal.valueOf(0));

                //TODO: Adding one user at once is slow, consider some batch process
                var oAuthCreateUserResult = oAuthServerAdminClient
                        .addUser(merchant.getEmail(), merchantRoles);

                if (oAuthCreateUserResult.isSuccess()) {
                    merchant.setAuthId(oAuthCreateUserResult.getResult());
                    merchant = merchantRepository.save(merchant);
                    merchantsImported.add(MerchantMapper.INSTANCE.toDto(merchant));
                }
            }

            reader.close();
            csvParser.close();
            return OperationResult.success(merchantsImported);
        } catch (Exception e) {
            //TODO: Check if failing user was created on auth server, rollback it there
            return OperationResult.failure("An error occurred while importing merchants: " + e.getMessage());
        }
    }

    /**
     * Remove merchant by authid.
     *
     * @param merchantId the auth id of merchant
     * @return the operation result
     */
    @Transactional
    @Override
    public OperationResult<Void> removeMerchantById(final String merchantId) {
        try {
            OperationResult<Boolean> hasTransactionsResult = transactionMsClient.getDoesMerchantHaveTransactions(merchantId);
            if (hasTransactionsResult.isSuccess() && hasTransactionsResult.getResult()) {
                return OperationResult.failure("Cannot remove merchant. Active transactions exist for the user.");
            }

            oAuthServerAdminClient.removeMerchantById(merchantId);
            merchantRepository.deleteByAuthId(merchantId);
            return OperationResult.success(null);
        } catch (Exception e) {
            return OperationResult.failure("An error occurred while removing the user: " + e.getMessage());
        }
    }

    /**
     * Remove all merchants both in db and on auth server.
     *
     * @param checkForActiveTransactions whether to check for active transactions.
     * If there are transactions for merchant, do nnot remove.
     * @return the operation result with the list of removed merchant auth ids
     */
    @Transactional
    @Override
    public OperationResult<List<String>> removeAllMerchants(boolean checkForActiveTransactions) {
        final String roleName = UserRoles.ROLE_MERCHANT;
        try {
            if (checkForActiveTransactions) {
                List<Merchant> allMerchants = merchantRepository.findAll();
                for (Merchant merchant : allMerchants) {
                    OperationResult<Boolean> hasTransactionsResult = transactionMsClient.getDoesMerchantHaveTransactions(merchant.getAuthId());
                    if (hasTransactionsResult.isSuccess() && hasTransactionsResult.getResult()) {
                        return OperationResult.failure("Cannot remove all merchants. Active transactions exist for at least one merchant.");
                    }
                }
            }

            OperationResult<List<String>> userIdsToRemoveResult = oAuthServerAdminClient.removeAllUsersWithRole(roleName);
            if (userIdsToRemoveResult.isSuccess()) {
                merchantRepository.deleteAllInBatch(); // for simplicity just cleanup the table
                return userIdsToRemoveResult;
            } else {
                return OperationResult.failure("Failed to remove users by role:" + roleName);
            }
        } catch (Exception e) {
            return OperationResult.failure("An error occurred while removing users by role: " + e.getMessage());
        }
    }
}