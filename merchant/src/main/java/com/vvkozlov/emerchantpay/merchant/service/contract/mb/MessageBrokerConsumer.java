package com.vvkozlov.emerchantpay.merchant.service.contract.mb;


import com.vvkozlov.emerchantpay.merchant.service.model.messagebroker.TransactionMbModel;

public interface MessageBrokerConsumer {
    public TransactionMbModel consumeMessage(TransactionMbModel merchantInfo);
}
