package com.khaier.document.controller;

import com.khaier.document.service.ApiAuthService;
import com.khaier.document.utils.CryptoUtils;
import com.khaier.document.utils.MobileUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class DocumentTemplateController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ApiAuthService apiAuthService;

    public DocumentTemplateController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, ApiAuthService apiAuthService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.apiAuthService = apiAuthService;
    }

    @GetMapping("/api/v1/document-templates")
    public Map<String, Object> listTemplates(
            HttpServletRequest request,
            @RequestParam String organizationNo,
            @RequestParam(required = false) String documentType
    ) {
        apiAuthService.verify(request, "");

        List<Map<String, Object>> items;

        if (documentType == null || documentType.isBlank()) {
            items = jdbcTemplate.queryForList("""
                    SELECT
                        id,
                        organization_no AS organizationNo,
                        document_type AS documentType,
                        template_name AS templateName,
                        template_format AS templateFormat,
                        version_no AS versionNo,
                        status,
                        is_active AS isActive,
                        created_at AS createdAt,
                        updated_at AS updatedAt
                    FROM document_templates
                    WHERE organization_no = ?
                    ORDER BY document_type, version_no DESC
                    """, organizationNo);
        } else {
            items = jdbcTemplate.queryForList("""
                    SELECT
                        id,
                        organization_no AS organizationNo,
                        document_type AS documentType,
                        template_name AS templateName,
                        template_format AS templateFormat,
                        version_no AS versionNo,
                        status,
                        is_active AS isActive,
                        created_at AS createdAt,
                        updated_at AS updatedAt
                    FROM document_templates
                    WHERE organization_no = ?
                      AND document_type = ?
                    ORDER BY version_no DESC
                    """, organizationNo, documentType);
        }

        return Map.of("success", true, "count", items.size(), "items", items);
    }

    @PostMapping(value = "/api/v1/document-templates", consumes = "application/json")
    public Map<String, Object> createTemplate(HttpServletRequest request, @RequestBody String rawBody) throws Exception {
        apiAuthService.verify(request, rawBody);

        Map<String, Object> body = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});

        String organizationNo = required(body, "organizationNo");
        String documentType = required(body, "documentType");
        String templateName = required(body, "templateName");
        String htmlContent = required(body, "htmlContent");
        String templateFormat = body.get("templateFormat") == null ? "html" : String.valueOf(body.get("templateFormat"));
        boolean activate = body.get("activate") == null || Boolean.parseBoolean(String.valueOf(body.get("activate")));

        Integer versionNo = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version_no), 0) + 1
                FROM document_templates
                WHERE organization_no = ?
                  AND document_type = ?
                """, Integer.class, organizationNo, documentType);

        if (activate) {
            jdbcTemplate.update("""
                    UPDATE document_templates
                    SET is_active = 0, status = 'inactive'
                    WHERE organization_no = ?
                      AND document_type = ?
                    """, organizationNo, documentType);
        }

        jdbcTemplate.update("""
                INSERT INTO document_templates
                (
                    organization_no,
                    document_type,
                    template_name,
                    template_format,
                    html_content,
                    version_no,
                    status,
                    is_active,
                    created_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                organizationNo,
                documentType,
                templateName,
                templateFormat,
                htmlContent,
                versionNo,
                activate ? "active" : "draft",
                activate ? 1 : 0,
                "api"
        );

        return Map.of(
                "success", true,
                "organizationNo", organizationNo,
                "documentType", documentType,
                "versionNo", versionNo,
                "status", activate ? "active" : "draft"
        );
    }

    @PostMapping("/api/v1/document-templates/{templateId}/activate")
    public Map<String, Object> activateTemplate(
            HttpServletRequest request,
            @PathVariable Long templateId,
            @RequestBody(required = false) String rawBody
    ) {
        apiAuthService.verify(request, rawBody == null ? "" : rawBody);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT organization_no, document_type
                FROM document_templates
                WHERE id = ?
                LIMIT 1
                """, templateId);

        if (rows.isEmpty()) {
            return Map.of("success", false, "message", "Template not found");
        }

        String organizationNo = String.valueOf(rows.get(0).get("organization_no"));
        String documentType = String.valueOf(rows.get(0).get("document_type"));

        jdbcTemplate.update("""
                UPDATE document_templates
                SET is_active = 0, status = 'inactive'
                WHERE organization_no = ?
                  AND document_type = ?
                """, organizationNo, documentType);

        jdbcTemplate.update("""
                UPDATE document_templates
                SET is_active = 1, status = 'active'
                WHERE id = ?
                """, templateId);

        return Map.of(
                "success", true,
                "templateId", templateId,
                "organizationNo", organizationNo,
                "documentType", documentType
        );
    }

    private String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return String.valueOf(value);
    }
}
