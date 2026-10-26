package com.kadaibook.service;

import com.kadaibook.domain.Shop;
import com.kadaibook.domain.User;
import com.kadaibook.repository.ShopRepository;
import com.kadaibook.repository.UserRepository;
import com.kadaibook.security.UserPrincipal;
import com.kadaibook.web.ApiException;
import com.kadaibook.web.dto.ShopDtos.CreateShopRequest;
import com.kadaibook.web.dto.ShopDtos.ShopDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ShopService(ShopRepository shopRepository, UserRepository userRepository, AuditService auditService) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ShopDto> list(UserPrincipal principal) {
        return shopRepository.findAllByOwnerIdOrderByCreatedAtAsc(principal.id()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ShopDto create(CreateShopRequest request, UserPrincipal principal) {
        if (!request.consent()) {
            throw ApiException.badRequest("consent_required", "Written consent is required before collecting pages.");
        }
        User owner = userRepository.findById(principal.id()).orElseThrow(() -> ApiException.unauthorized("Sign in again."));
        Shop shop = new Shop();
        shop.setName(request.name().trim());
        shop.setOwner(owner);
        shop.setConsentAt(Instant.now());
        shop = shopRepository.save(shop);
        auditService.record(
                shop,
                owner,
                "shop.created",
                "shop",
                shop.getId(),
                null,
                Map.of("name", shop.getName(), "consent", true));
        return toDto(shop);
    }

    @Transactional(readOnly = true)
    public ShopDto get(UUID shopId, UserPrincipal principal) {
        return toDto(find(shopId, principal));
    }

    Shop find(UUID shopId, UserPrincipal principal) {
        return shopRepository.findByIdAndOwnerId(shopId, principal.id())
                .orElseThrow(() -> ApiException.notFound("Shop not found"));
    }

    private ShopDto toDto(Shop shop) {
        return new ShopDto(shop.getId(), shop.getName(), shop.getConsentAt(), shop.getCreatedAt());
    }
}
