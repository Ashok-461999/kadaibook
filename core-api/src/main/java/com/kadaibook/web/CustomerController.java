package com.kadaibook.web;

import com.kadaibook.security.UserPrincipal;
import com.kadaibook.service.CustomerService;
import com.kadaibook.web.dto.CustomerDtos.CreateCustomerRequest;
import com.kadaibook.web.dto.CustomerDtos.CustomerDto;
import com.kadaibook.web.dto.CustomerDtos.CustomerLedgerDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<CustomerDto> list(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return customerService.list(shopId, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDto create(
            @PathVariable UUID shopId,
            @Valid @RequestBody CreateCustomerRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return customerService.create(shopId, request, principal);
    }

    @GetMapping("/{customerId}/ledger")
    public CustomerLedgerDto ledger(
            @PathVariable UUID shopId,
            @PathVariable UUID customerId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return customerService.ledger(shopId, customerId, principal);
    }
}
