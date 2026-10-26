package com.kadaibook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kadaibook.domain.AuditLog;
import com.kadaibook.domain.Shop;
import com.kadaibook.domain.User;
import com.kadaibook.repository.AuditLogRepository;
import com.kadaibook.web.dto.AuditDtos.AuditEntryDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(
            Shop shop,
            User actor,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> before,
            Map<String, Object> after) {
        AuditLog entry = new AuditLog();
        entry.setShop(shop);
        entry.setActor(actor);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBeforeJson(json(before));
        entry.setAfterJson(json(after));
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditEntryDto> list(UUID shopId, int limit) {
        int bounded = Math.max(1, Math.min(limit, 200));
        return auditLogRepository.findAllByShopIdOrderByAtDesc(shopId, PageRequest.of(0, bounded)).stream()
                .map(log -> new AuditEntryDto(
                        log.getId(),
                        log.getAction(),
                        log.getEntityType(),
                        log.getEntityId(),
                        log.getActor() == null ? null : log.getActor().getDisplayName(),
                        log.getBeforeJson(),
                        log.getAfterJson(),
                        log.getAt()))
                .toList();
    }

    private String json(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.warn("could not serialise an audit payload");
            return null;
        }
    }
}
