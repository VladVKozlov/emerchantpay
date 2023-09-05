package com.vvkozlov.emerchantpay.merchant.controller.api;

import com.vvkozlov.emerchantpay.merchant.domain.constants.MerchantStatusEnum;
import com.vvkozlov.emerchantpay.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller to manage merchants and import admins.
 */
@RestController
@RequestMapping("/api/merchants")
public class MerchantApiController {

    private final MerchantService merchantService;

    @Autowired
    public MerchantApiController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @Operation(summary = "Get a merchant status by uuid", description = "Returns a merchant status data as per the uuid.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved"),
            @ApiResponse(responseCode = "404", description = "The merchant was not found")
    })
    @GetMapping("{id}/status")
    public ResponseEntity<Boolean> getIsMerchantActive(@PathVariable String id) {
        var operationResult = merchantService.getMerchant(id);
        if (operationResult.isSuccess()) {
            boolean isActive = operationResult.getResult().getStatus() == MerchantStatusEnum.ACTIVE;
            return ResponseEntity.ok(isActive);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}