package com.kadaibook.service;

import com.kadaibook.domain.Shop;
import com.kadaibook.repository.ShopRepository;
import com.kadaibook.security.UserPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccessService {

    private final ShopRepository shopRepository;

    public AccessService(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    public Shop requireShop(UUID shopId, UserPrincipal principal) {
        return shopRepository.findByIdAndOwnerId(shopId, principal.id())
                .orElseThrow(() -> com.kadaibook.web.ApiException.notFound("Shop not found"));
    }
}
