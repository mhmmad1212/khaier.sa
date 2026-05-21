package com.khaier.attendance.controller;

import com.khaier.attendance.service.ApiAuthService;
import com.khaier.attendance.utils.CryptoUtils;
import com.khaier.attendance.utils.MobileUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
public class CreateDocumentController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ApiAuthService apiAuthService;

    public CreateDocumentController(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ApiAuthService apiAuthService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.apiAuthService = apiAuthService;
    }

    @PostMapping(value = "/api/v1/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> createDocument(
            HttpServletRequest httpRequest,
            @RequestBody String rawBody
    ) throws Exception {

        apiAuthService.verify(httpRequest, rawBody);

        Map<String, Object> req = objectMapper.readValue(rawBody, new TypeReference<>() {});

        String organizationNo = required(req, "organizationNo");
        String organizationName = required(req, "organizationName");
        String documentType = required(req, "documentType");
        String internalDocumentNo = stringValue(req.get("internalDocumentNo"));
        String documentDateRaw = stringValue(req.get("documentDate"));
        String mobileRaw = stringValue(req.get("mobile"));
        String referenceSystem = stringValue(req.get("referenceSystem"));
        String referenceId = stringValue(req.get("referenceId"));
        String idempotencyKey = stringValue(req.get("idempotencyKey"));

        Object documentDataObj = req.get("documentData");
        if (!(documentDataObj instanceof Map)) {
            throw new IllegalArgumentException("documentData is required and must be an object");
        }

        String documentDataJson = objectMapper.writeValueAsString(documentDataObj);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            List<Map<String, Object>> existing = jdbcTemplate.queryForList("""
                    SELECT short_code, document_hash
                    FROM verified_documents
                    WHERE organization_no = ? AND idempotency_key = ?
                    LIMIT 1
                    """, organizationNo, idempotencyKey);

            if (!existing.isEmpty()) {
                String shortCode = String.valueOf(existing.get(0).get("short_code"));
                return Map.of(
                        "success", true,
                        "duplicate", true,
                        "shortCode", shortCode,
                        "verificationUrl", "https://khaier.sa/" + shortCode,
                        "qrCodeUrl", "https://khaier.sa/qr/" + shortCode,
                        "documentHash", String.valueOf(existing.get(0).get("document_hash"))
                );
            }
        }

        String normalizedMobile = MobileUtils.normalizeSaudiMobile(mobileRaw);
        String mobileHash = normalizedMobile.isBlank() ? null : CryptoUtils.sha256Hex(normalizedMobile);
        String mobileLast4 = MobileUtils.last4(normalizedMobile);

        String hashMaterial = organizationNo + "|"
                + organizationName + "|"
                + documentType + "|"
                + nullToEmpty(internalDocumentNo) + "|"
                + nullToEmpty(documentDateRaw) + "|"
                + documentDataJson;

        String documentHash = CryptoUtils.sha256Hex(hashMaterial);

        String shortCode = generateUniqueShortCode();

        Timestamp documentDate = parseDate(documentDateRaw);

        jdbcTemplate.update("""
                INSERT INTO verified_documents
                (
                    short_code,
                    organization_no,
                    organization_name,
                    document_type,
                    internal_document_no,
                    reference_system,
                    reference_id,
                    idempotency_key,
                    mobile_hash,
                    mobile_last4,
                    document_date,
                    document_data,
                    document_hash,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, 'active')
                """,
                shortCode,
                organizationNo,
                organizationName,
                documentType,
                internalDocumentNo,
                referenceSystem,
                referenceId,
                idempotencyKey,
                mobileHash,
                mobileLast4,
                documentDate,
                documentDataJson,
                documentHash
        );

        jdbcTemplate.update("""
                INSERT INTO document_access_logs (short_code, action, ip_address, user_agent, details)
                VALUES (?, 'document_created', ?, ?, CAST(? AS JSON))
                """,
                shortCode,
                clientIp(httpRequest),
                httpRequest.getHeader("User-Agent"),
                "{\"source\":\"api\"}"
        );

        return Map.of(
                "success", true,
                "duplicate", false,
                "shortCode", shortCode,
                "verificationUrl", "https://khaier.sa/" + shortCode,
                "qrCodeUrl", "https://khaier.sa/qr/" + shortCode,
                "documentHash", documentHash
        );
    }

    private String generateUniqueShortCode() {
        for (int i = 0; i < 10; i++) {
            String code = CryptoUtils.randomBase62(8);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM verified_documents WHERE short_code = ?",
                    Integer.class,
                    code
            );
            if (count != null && count == 0) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate unique short code");
    }

    private String required(Map<String, Object> req, String key) {
        String value = stringValue(req.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private String stringValue(Object value) {
        if (value == null) return null;
        return String.valueOf(value).trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Timestamp parseDate(String value) {
        if (value == null || value.isBlank()) return null;

        try {
            if (value.length() == 10) {
                return Timestamp.valueOf(LocalDate.parse(value).atStartOfDay());
            }
            return Timestamp.valueOf(LocalDateTime.parse(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
