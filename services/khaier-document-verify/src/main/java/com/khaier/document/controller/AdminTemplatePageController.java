package com.khaier.document.controller;

import com.khaier.document.service.ApiAuthService;
import com.khaier.document.utils.CryptoUtils;
import com.khaier.document.utils.MobileUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AdminTemplatePageController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${ADMIN_KEY:}")
    private String adminKey;

    public AdminTemplatePageController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping(value = "/admin/templates", produces = MediaType.TEXT_HTML_VALUE)
    public String page(
            @RequestParam String key,
            @RequestParam(defaultValue = "1001") String organizationNo,
            @RequestParam(defaultValue = "receipt_voucher") String documentType
    ) {
        checkKey(key);

        List<Map<String, Object>> templates = jdbcTemplate.queryForList("""
                SELECT
                    id,
                    organization_no,
                    document_type,
                    template_name,
                    template_format,
                    version_no,
                    status,
                    is_active,
                    created_at,
                    updated_at,
                    LENGTH(html_content) AS html_len
                FROM document_templates
                WHERE organization_no = ?
                  AND document_type = ?
                ORDER BY version_no DESC, id DESC
                """, organizationNo, documentType);

        StringBuilder rows = new StringBuilder();

        if (templates.isEmpty()) {
            rows.append("<tr><td colspan='9' class='empty'>لا توجد قوالب لهذه الجمعية وهذا النوع.</td></tr>");
        } else {
            for (Map<String, Object> t : templates) {
                String id = safe(t.get("id"));
                boolean active = "1".equals(String.valueOf(t.get("is_active")))
                        || "true".equalsIgnoreCase(String.valueOf(t.get("is_active")));

                rows.append("<tr>");
                rows.append("<td>").append(id).append("</td>");
                rows.append("<td>").append(safe(t.get("template_name"))).append("</td>");
                rows.append("<td>").append(safe(t.get("version_no"))).append("</td>");
                rows.append("<td>").append(active ? "<span class='active'>نشط</span>" : "<span class='inactive'>موقوف</span>").append("</td>");
                rows.append("<td>").append(safe(t.get("status"))).append("</td>");
                rows.append("<td>").append(safe(t.get("html_len"))).append("</td>");
                rows.append("<td>").append(safe(t.get("created_at"))).append("</td>");

                rows.append("<td class='actions'>");
                rows.append("<a class='linkBtn' target='_blank' href='/admin/templates/")
                        .append(id)
                        .append("/preview?key=")
                        .append(enc(key))
                        .append("'>معاينة</a>");

                rows.append("<a class='linkBtn gray' target='_blank' href='/admin/templates/")
                        .append(id)
                        .append("/source?key=")
                        .append(enc(key))
                        .append("'>عرض HTML</a>");

                if (!active) {
                    rows.append("<form method='post' action='/admin/templates/")
                            .append(id)
                            .append("/activate' style='display:inline'>")
                            .append("<input type='hidden' name='key' value='").append(safeAttr(key)).append("'>")
                            .append("<input type='hidden' name='organizationNo' value='").append(safeAttr(organizationNo)).append("'>")
                            .append("<input type='hidden' name='documentType' value='").append(safeAttr(documentType)).append("'>")
                            .append("<button class='smallBtn' type='submit'>تفعيل</button>")
                            .append("</form>");
                }
                rows.append("</td>");

                rows.append("</tr>");
            }
        }

        return "<!doctype html><html lang='ar' dir='rtl'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>إدارة قوالب الوثائق</title>"
                + "<style>"
                + "body{font-family:Arial,Tahoma,sans-serif;background:#f3f7f6;color:#111827;padding:24px}"
                + ".wrap{max-width:1200px;margin:auto}"
                + ".card{background:white;border-radius:18px;box-shadow:0 10px 25px rgba(0,0,0,.07);padding:24px;margin-bottom:20px}"
                + "h1{color:#0f766e;margin-top:0}"
                + "label{display:block;color:#64748b;margin:10px 0 6px}"
                + "input,textarea{width:100%;box-sizing:border-box;padding:12px;border:1px solid #d1d5db;border-radius:10px;font-size:15px}"
                + "textarea{min-height:300px;direction:ltr;font-family:Consolas,monospace;font-size:13px}"
                + "button,.linkBtn{background:#0f766e;color:white;border:0;border-radius:10px;padding:10px 14px;cursor:pointer;font-size:14px;text-decoration:none;display:inline-block;margin:2px}"
                + ".smallBtn{background:#1d4ed8}"
                + ".gray{background:#475569}"
                + ".grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}"
                + "table{width:100%;border-collapse:collapse;margin-top:14px}"
                + "th,td{border-bottom:1px solid #e5e7eb;padding:11px;text-align:right;font-size:14px;vertical-align:top}"
                + "th{background:#f8fafc;color:#475569}"
                + ".active{background:#dcfce7;color:#166534;padding:5px 10px;border-radius:999px;font-weight:bold}"
                + ".inactive{background:#fee2e2;color:#991b1b;padding:5px 10px;border-radius:999px;font-weight:bold}"
                + ".empty{text-align:center;color:#64748b;padding:25px}"
                + ".hint{background:#ecfdf5;border:1px solid #bbf7d0;color:#166534;padding:12px;border-radius:12px;margin-top:12px}"
                + ".actions{white-space:nowrap}"
                + "@media(max-width:900px){.grid{grid-template-columns:1fr}body{padding:12px}table{display:block;overflow:auto}}"
                + "</style></head><body><div class='wrap'>"

                + "<div class='card'>"
                + "<h1>إدارة قوالب الوثائق</h1>"
                + "<form method='get' action='/admin/templates'>"
                + "<input type='hidden' name='key' value='" + safeAttr(key) + "'>"
                + "<div class='grid'>"
                + "<div><label>رقم الجمعية</label><input name='organizationNo' value='" + safeAttr(organizationNo) + "'></div>"
                + "<div><label>نوع الوثيقة</label><input name='documentType' value='" + safeAttr(documentType) + "'></div>"
                + "</div>"
                + "<p><button type='submit'>عرض القوالب</button></p>"
                + "</form>"
                + "<div class='hint'>القالب النشط هو المستخدم عند إصدار PDF. زر المعاينة يعرض القالب ببيانات تجريبية.</div>"
                + "</div>"

                + "<div class='card'>"
                + "<h2>القوالب الحالية</h2>"
                + "<table>"
                + "<thead><tr><th>ID</th><th>اسم القالب</th><th>الإصدار</th><th>الحالة</th><th>Status</th><th>حجم HTML</th><th>تاريخ الإنشاء</th><th>إجراءات</th></tr></thead>"
                + "<tbody>" + rows + "</tbody>"
                + "</table>"
                + "</div>"

                + "<div class='card'>"
                + "<h2>رفع قالب HTML جديد</h2>"
                + "<form method='post' action='/admin/templates'>"
                + "<input type='hidden' name='key' value='" + safeAttr(key) + "'>"
                + "<div class='grid'>"
                + "<div><label>رقم الجمعية</label><input name='organizationNo' value='" + safeAttr(organizationNo) + "' required></div>"
                + "<div><label>نوع الوثيقة</label><input name='documentType' value='" + safeAttr(documentType) + "' required></div>"
                + "</div>"
                + "<label>اسم القالب</label><input name='templateName' value='قالب جديد' required>"
                + "<label>كود HTML</label>"
                + "<textarea name='htmlContent' required placeholder='ضع كود HTML هنا، واستخدم المتغيرات مثل {{organizationName}} و {{amount}} و {{qrBase64}}'></textarea>"
                + "<p><label><input type='checkbox' name='activate' value='true' checked style='width:auto'> تفعيل القالب مباشرة</label></p>"
                + "<button type='submit'>حفظ القالب</button>"
                + "</form>"
                + "</div>"

                + "</div></body></html>";
    }

    @GetMapping(value = "/admin/templates/{templateId}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String preview(@PathVariable Long templateId, @RequestParam String key) {
        checkKey(key);

        Map<String, Object> t = loadTemplate(templateId);
        String html = String.valueOf(t.get("html_content"));

        return renderTemplateWithSampleData(html);
    }

    @GetMapping(value = "/admin/templates/{templateId}/source", produces = MediaType.TEXT_HTML_VALUE)
    public String source(@PathVariable Long templateId, @RequestParam String key) {
        checkKey(key);

        Map<String, Object> t = loadTemplate(templateId);
        String html = String.valueOf(t.get("html_content"));

        return "<!doctype html><html lang='ar' dir='rtl'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>عرض كود القالب</title>"
                + "<style>body{font-family:Arial,Tahoma;background:#f3f7f6;padding:24px}.card{background:white;border-radius:18px;padding:24px;box-shadow:0 10px 25px rgba(0,0,0,.07)}"
                + "textarea{width:100%;min-height:650px;direction:ltr;font-family:Consolas,monospace;font-size:13px;padding:12px;box-sizing:border-box}"
                + "h1{color:#0f766e}</style></head><body><div class='card'>"
                + "<h1>كود HTML للقالب: " + safe(t.get("template_name")) + "</h1>"
                + "<textarea readonly>" + safe(html) + "</textarea>"
                + "</div></body></html>";
    }

    @PostMapping(value = "/admin/templates", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> createTemplate(
            @RequestParam String key,
            @RequestParam String organizationNo,
            @RequestParam String documentType,
            @RequestParam String templateName,
            @RequestParam String htmlContent,
            @RequestParam(required = false) String activate
    ) {
        checkKey(key);

        boolean active = "true".equalsIgnoreCase(activate);

        Integer versionNo = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version_no), 0) + 1
                FROM document_templates
                WHERE organization_no = ?
                  AND document_type = ?
                """, Integer.class, organizationNo, documentType);

        if (active) {
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
                VALUES (?, ?, ?, 'html', ?, ?, ?, ?, 'admin')
                """,
                organizationNo,
                documentType,
                templateName,
                htmlContent,
                versionNo,
                active ? "active" : "draft",
                active ? 1 : 0
        );

        return redirect(key, organizationNo, documentType);
    }

    @PostMapping(value = "/admin/templates/{templateId}/activate", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> activateTemplate(
            @PathVariable Long templateId,
            @RequestParam String key,
            @RequestParam String organizationNo,
            @RequestParam String documentType
    ) {
        checkKey(key);

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

        return redirect(key, organizationNo, documentType);
    }

    private Map<String, Object> loadTemplate(Long templateId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, template_name, html_content
                FROM document_templates
                WHERE id = ?
                LIMIT 1
                """, templateId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
        }

        return rows.get(0);
    }

    private String renderTemplateWithSampleData(String html) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("organizationName", "جمعية البر والخدمات الاجتماعية");
        vars.put("receiptNo", "RV-2026-000145");
        vars.put("receiptDate", "2026-05-20");
        vars.put("donorName", "فاعل خير");
        vars.put("amount", "500");
        vars.put("donationType", "صدقة عامة");
        vars.put("paymentMethod", "مدى");
        vars.put("shortCode", "0ulj22oR");
        vars.put("verificationUrl", "https://khaier.sa/0ulj22oR");
        vars.put("qrBase64", "/qr/0ulj22oR");
        vars.put("statusText", "الوثيقة صحيحة وفعالة");

        String result = html;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue());
            result = result.replace("{{ " + e.getKey() + " }}", e.getValue());
        }

        return result;
    }

    private ResponseEntity<Void> redirect(String key, String organizationNo, String documentType) {
        String url = "/admin/templates?key=" + enc(key)
                + "&organizationNo=" + enc(organizationNo)
                + "&documentType=" + enc(documentType);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    private void checkKey(String key) {
        if (adminKey == null || adminKey.isBlank() || key == null || !adminKey.equals(key)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String safe(Object value) {
        if (value == null) return "-";
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String safeAttr(Object value) {
        return safe(value).replace("'", "&#39;");
    }
}
