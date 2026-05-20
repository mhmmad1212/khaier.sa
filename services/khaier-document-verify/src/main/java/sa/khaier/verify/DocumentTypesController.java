package sa.khaier.verify;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class DocumentTypesController {

    private final JdbcTemplate jdbcTemplate;

    public DocumentTypesController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/v1/document-types")
    public Map<String, Object> listDocumentTypes() {
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT
                    code,
                    name_ar AS nameAr,
                    name_en AS nameEn,
                    default_security_level AS defaultSecurityLevel,
                    is_active AS isActive
                FROM document_types
                WHERE is_active = 1
                ORDER BY id
                """);

        return Map.of(
                "success", true,
                "count", items.size(),
                "items", items
        );
    }
}
