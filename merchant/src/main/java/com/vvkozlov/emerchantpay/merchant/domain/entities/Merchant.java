package com.vvkozlov.emerchantpay.merchant.domain.entities;

import com.vvkozlov.emerchantpay.merchant.domain.constants.MerchantStatusEnum;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "merchant")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Merchant {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    private String name;
    private String description;
    private String email;

    @Enumerated(EnumType.STRING)
    private MerchantStatusEnum status;

    @Column(name = "total_transaction_sum")
    private Double totalTransactionSum;
}