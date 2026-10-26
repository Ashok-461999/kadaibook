package com.kadaibook.web;

import com.kadaibook.security.UserPrincipal;
import com.kadaibook.service.AuditService;
import com.kadaibook.service.AccessService;
import com.kadaibook.service.ShopService;
import com.kadaibook.web.dto.AuditDtos.AuditEntryDto;
import com.kadaibook.web.dto.ShopDtos.CreateShopRequest;
import com.kadaibook.web.dto.ShopDtos.ShopDto;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shops")
public class ShopController {

    private final ShopService shopService;
    private final AccessService accessService;
    private final AuditService auditService;

    public ShopController(ShopService shopService, AccessService accessService, AuditService auditService) {
        this.shopService = shopService;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<ShopDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return shopService.list(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShopDto create(
            @Valid @RequestBody CreateShopRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shopService.create(request, principal);
    }

    @GetMapping("/{shopId}")
    public ShopDto get(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return shopService.get(shopId, principal);
    }

    @GetMapping("/{shopId}/audit")
    public List<AuditEntryDto> audit(
            @PathVariable UUID shopId,
            @RequestParam(defaultValue = "100") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {
        accessService.requireShop(shopId, principal);
        return auditService.list(shopId, limit);
    }
}
