package com.khaier.attendance.controller;

import com.khaier.attendance.service.ApiAuthService;
import com.khaier.attendance.utils.CryptoUtils;
import com.khaier.attendance.utils.MobileUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PublicVerifyController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PublicVerifyController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        return "<!doctype html><html lang='ar' dir='rtl'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>خدمة التحقق من الوثائق</title>"
                + "<style>body{font-family:Arial,Tahoma;background:#f7faf9;color:#1f2937;text-align:center;padding:60px}"
                + ".box{background:white;max-width:760px;margin:auto;padding:36px;border-radius:18px;box-shadow:0 10px 30px rgba(0,0,0,.08)}"
                + "h1{color:#0f766e}</style></head><body><div class='box'>"
                + "<h1>خدمة التحقق من الوثائق</h1>"
                + "<p>خدمة إلكترونية للتحقق من صحة الوثائق الصادرة من نظام خير.</p>"
                + "</div></body></html>";
    }

    @GetMapping(value = "/{shortCode:[A-Za-z0-9]{6,20}}", produces = MediaType.TEXT_HTML_VALUE)
    public String verifyPage(@PathVariable String shortCode) throws Exception {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT vd.short_code, vd.organization_name, vd.document_type, dt.name_ar AS document_type_name, "
                        + "vd.internal_document_no, vd.status, vd.document_data "
                        + "FROM verified_documents vd "
                        + "LEFT JOIN document_types dt ON dt.code = vd.document_type "
                        + "WHERE vd.short_code = ? LIMIT 1",
                shortCode
        );

        if (rows.isEmpty()) {
            return notFound(shortCode);
        }

        Map<String, Object> doc = rows.get(0);
        Map<String, Object> data = new LinkedHashMap<>();

        Object raw = doc.get("document_data");
        if (raw != null) {
            data = objectMapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {});
        }

        String documentTypeName = doc.get("document_type_name") == null
                ? String.valueOf(doc.get("document_type"))
                : String.valueOf(doc.get("document_type_name"));

        StringBuilder b = new StringBuilder();

        b.append("<!doctype html><html lang='ar' dir='rtl'><head><meta charset='utf-8'>");
        b.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        b.append("<title>التحقق من الوثيقة</title>");
        b.append("<style>");
        b.append("body{font-family:Arial,Tahoma,sans-serif;background:#f3f7f6;color:#1f2937;padding:24px}");
        b.append(".card{background:white;max-width:860px;margin:auto;border-radius:20px;box-shadow:0 10px 28px rgba(0,0,0,.08);overflow:hidden}");
        b.append(".head{background:#0f766e;color:white;padding:24px;text-align:center}");
        b.append(".body{padding:28px}");
        b.append(".row{display:flex;justify-content:space-between;border-bottom:1px solid #edf2f7;padding:14px 0;gap:16px}");
        b.append(".label{color:#64748b}.value{font-weight:bold;text-align:left}.ltr{direction:ltr}");
        b.append(".ok{display:inline-block;background:#dcfce7;color:#166534;padding:8px 14px;border-radius:999px;font-weight:bold}");
        b.append(".warn{display:inline-block;background:#fee2e2;color:#991b1b;padding:8px 14px;border-radius:999px;font-weight:bold}");
        b.append(".download{margin-top:28px;background:#f8fafc;border:1px solid #e5e7eb;border-radius:16px;padding:20px}");
        b.append("input{width:100%;box-sizing:border-box;padding:14px;border:1px solid #d1d5db;border-radius:12px;font-size:16px;margin:10px 0}");
        b.append("button{background:#0f766e;color:white;border:0;border-radius:12px;padding:13px 22px;font-size:16px;cursor:pointer}");
        b.append(".footer{text-align:center;color:#64748b;font-size:13px;margin-top:22px}");
        b.append("</style></head><body>");

        b.append("<div class='card'><div class='head'><h1>التحقق من الوثيقة</h1>");
        b.append("<p>خدمة التحقق من الوثائق الصادرة من نظام خير</p></div><div class='body'>");

        if ("active".equals(String.valueOf(doc.get("status")))) {
            b.append("<p><span class='ok'>الوثيقة صحيحة وفعالة</span></p>");
        } else {
            b.append("<p><span class='warn'>الوثيقة غير فعالة</span></p>");
        }

        b.append(row("اسم الجمعية", doc.get("organization_name"), false));
        b.append(row("نوع الوثيقة", documentTypeName, false));
        b.append(row("رقم الوثيقة", doc.get("internal_document_no"), true));
        b.append(row("كود التحقق", doc.get("short_code"), true));
        b.append(rowIf("رقم السند", data.get("receiptNo")));
        b.append(rowIf("اسم المتبرع", data.get("donorName")));
        b.append(rowIf("المبلغ", data.get("amount") == null ? null : data.get("amount") + " ريال"));
        b.append(rowIf("نوع التبرع", data.get("donationType")));
        b.append(rowIf("طريقة الدفع", data.get("paymentMethod")));
        b.append(rowIf("تاريخ السند", data.get("receiptDate")));

        b.append("<div class='download'><h3>تحميل الوثيقة</h3>");
        b.append("<p>لتحميل الوثيقة، أدخل رقم الجوال المرتبط بالوثيقة.</p>");
        b.append("<form method='get' action='/api/public/documents/");
        b.append(safeAttr(shortCode));
        b.append("/quick-download' target='_blank'>");
        b.append("<input name='mobile' placeholder='مثال: 0551628535' inputmode='numeric' required>");
        b.append("<button type='submit'>تحميل الوثيقة</button>");
        b.append("</form></div>");

        b.append("<div class='footer'>هذه الوثيقة صادرة من نظام خير لإدارة الجمعيات، وتم التحقق من بياناتها إلكترونيًا.</div>");
        b.append("</div></div></body></html>");

        return b.toString();
    }

    private String rowIf(String label, Object value) {
        if (value == null || String.valueOf(value).isBlank()) return "";
        return row(label, value, false);
    }

    private String row(String label, Object value, boolean ltr) {
        return "<div class='row'><div class='label'>" + safe(label) + "</div><div class='value " + (ltr ? "ltr" : "") + "'>" + safe(value) + "</div></div>";
    }

    private String notFound(String shortCode) {
        return "<html lang='ar' dir='rtl'><body style='font-family:Arial;text-align:center;padding:60px'>"
                + "<h1>لم يتم العثور على الوثيقة</h1><p>تعذر العثور على وثيقة مرتبطة بهذا الكود.</p>"
                + "<code>" + safe(shortCode) + "</code></body></html>";
    }

    private String safe(Object value) {
        if (value == null) return "-";
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String safeAttr(String value) {
        return safe(value).replace("'", "&#39;");
    }
}
