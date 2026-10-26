package com.kadaibook.service;

import com.kadaibook.domain.Customer;
import com.kadaibook.domain.LedgerEntry;
import com.kadaibook.domain.Shop;
import com.kadaibook.domain.User;
import com.kadaibook.money.Money;
import com.kadaibook.repository.CustomerRepository;
import com.kadaibook.repository.LedgerEntryRepository;
import com.kadaibook.repository.ShopRepository;
import com.kadaibook.repository.UserRepository;
import com.kadaibook.security.UserPrincipal;
import com.kadaibook.web.ApiException;
import com.kadaibook.web.dto.CustomerDtos.CreateCustomerRequest;
import com.kadaibook.web.dto.CustomerDtos.CustomerDto;
import com.kadaibook.web.dto.CustomerDtos.CustomerLedgerDto;
import com.kadaibook.web.dto.CustomerDtos.LedgerRowDto;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public CustomerService(
            CustomerRepository customerRepository,
            ShopRepository shopRepository,
            LedgerEntryRepository ledgerEntryRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.customerRepository = customerRepository;
        this.shopRepository = shopRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CustomerDto> list(UUID shopId, UserPrincipal principal) {
        Shop shop = requireShop(shopId, principal);
        return customerRepository.findAllByShopIdOrderByDisplayNameAsc(shop.getId()).stream()
                .map(customer -> toDto(
                        customer,
                        saturatedBalance(shop.getId(), customer.getId())))
                .toList();
    }

    @Transactional
    public CustomerDto create(UUID shopId, CreateCustomerRequest request, UserPrincipal principal) {
        Shop shop = requireShop(shopId, principal);
        String displayName = request.displayName().trim();
        String normalized = CustomerMatcher.normalise(displayName);
        if (normalized.isEmpty()) {
            throw ApiException.badRequest("invalid_customer_name", "Enter a customer name with letters or digits.");
        }
        if (normalized.length() > 200) {
            throw ApiException.badRequest("invalid_customer_name", "The customer name is too long.");
        }
        if (customerRepository.existsByShopIdAndNormalizedName(shop.getId(), normalized)) {
            throw ApiException.conflict("customer_exists", "A customer with this name already exists.");
        }
        Set<String> aliases = new LinkedHashSet<>();
        if (request.aliases() != null) {
            for (String alias : request.aliases()) {
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                String trimmed = alias.trim();
                if (trimmed.length() > 200) {
                    throw ApiException.badRequest("invalid_alias", "A customer alias is too long.");
                }
                aliases.add(trimmed);
            }
        }
        Customer customer = new Customer();
        customer.setShop(shop);
        customer.setDisplayName(displayName);
        customer.setNormalizedName(normalized);
        customer.setAliases(new ArrayList<>(aliases));
        customer = customerRepository.save(customer);
        User actor = userRepository.findById(principal.id()).orElse(null);
        auditService.record(
                shop,
                actor,
                "customer.created",
                "customer",
                customer.getId(),
                null,
                Map.of("display_name", customer.getDisplayName(), "aliases", aliases));
        return toDto(customer, 0L);
    }

    @Transactional(readOnly = true)
    public CustomerLedgerDto ledger(UUID shopId, UUID customerId, UserPrincipal principal) {
        Shop shop = requireShop(shopId, principal);
        Customer customer = customerRepository.findByIdAndShopId(customerId, shop.getId())
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        List<LedgerRowDto> rows = ledgerEntryRepository
                .findLedger(shop.getId(), customerId)
                .stream()
                .map(this::toRow)
                .toList();
        return new CustomerLedgerDto(
                customer.getId(),
                customer.getDisplayName(),
                saturatedBalance(shop.getId(), customerId),
                rows);
    }

    private LedgerRowDto toRow(LedgerEntry entry) {
        return new LedgerRowDto(
                entry.getId(),
                entry.getEntryDate(),
                entry.getDescription(),
                entry.getDebitPaise(),
                entry.getCreditPaise(),
                entry.getSourcePage() == null ? null : entry.getSourcePage().getId(),
                entry.getCreatedAt());
    }

    private CustomerDto toDto(Customer customer, long balance) {
        return new CustomerDto(
                customer.getId(),
                customer.getDisplayName(),
                customer.getAliases() == null ? List.of() : List.copyOf(customer.getAliases()),
                customer.getCreatedAt(),
                balance);
    }

    private long saturatedBalance(UUID shopId, UUID customerId) {
        try {
            return ledgerEntryRepository.sumBalance(shopId, customerId);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private Shop requireShop(UUID shopId, UserPrincipal principal) {
        return shopRepository.findByIdAndOwnerId(shopId, principal.id())
                .orElseThrow(() -> ApiException.notFound("Shop not found"));
    }
}
