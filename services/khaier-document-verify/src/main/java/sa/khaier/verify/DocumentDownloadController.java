package sa.khaier.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class DocumentDownloadController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DocumentDownloadController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/api/public/documents/{shortCode}/quick-download")
    public ResponseEntity<byte[]> quickDownload(@PathVariable String shortCode, @RequestParam String mobile) throws Exception {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT organization_no, organization_name, document_type, internal_document_no, document_data, short_code, mobile_hash, status "
                        + "FROM verified_documents WHERE short_code = ? LIMIT 1",
                shortCode
        );

        if (rows.isEmpty()) {
            return htmlError("تعذر التحقق من البيانات المدخلة");
        }

        Map<String, Object> doc = rows.get(0);

        if (!"active".equals(String.valueOf(doc.get("status")))) {
            return htmlError("تعذر التحقق من البيانات المدخلة");
        }

        String normalized = MobileUtils.normalizeSaudiMobile(mobile);
        String mobileHash = normalized.isBlank() ? "" : CryptoUtils.sha256Hex(normalized);
        String storedHash = String.valueOf(doc.get("mobile_hash"));

        if (!CryptoUtils.secureEquals(storedHash, mobileHash)) {
            return htmlError("تعذر التحقق من البيانات المدخلة");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        Object raw = doc.get("document_data");
        if (raw != null) {
            data = objectMapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {});
        }

        String code = value(doc.get("short_code"), "-");
        String organizationNo = value(doc.get("organization_no"), "-");
        String documentType = value(doc.get("document_type"), "-");

        String qrBase64 = qrDataUri("https://khaier.sa/" + code);
        Map<String, String> vars = buildVars(doc, data, qrBase64);

        String template = loadActiveTemplate(organizationNo, documentType);
        String html = (template == null || template.isBlank())
                ? fallbackReceiptHtml(vars)
                : renderTemplate(template, vars);

        byte[] pdf = renderPdf(html, code);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename("receipt-" + code + ".pdf").build());
        headers.setCacheControl(CacheControl.noCache());

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/api/public/documents/{shortCode}/request-download", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> requestDownload(@PathVariable String shortCode, @RequestBody Map<String, Object> request) {
        String mobile = request.get("mobile") == null ? "" : String.valueOf(request.get("mobile"));
        String url = "/api/public/documents/" + shortCode + "/quick-download?mobile=" + mobile;
        return Map.of("success", true, "downloadUrl", url, "expiresInSeconds", 300);
    }

    private String loadActiveTemplate(String organizationNo, String documentType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT html_content
                FROM document_templates
                WHERE organization_no = ?
                  AND document_type = ?
                  AND template_format = 'html'
                  AND status = 'active'
                  AND is_active = 1
                ORDER BY version_no DESC
                LIMIT 1
                """, organizationNo, documentType);

        if (rows.isEmpty()) return null;

        Object html = rows.get(0).get("html_content");
        return html == null ? null : String.valueOf(html);
    }

    private Map<String, String> buildVars(Map<String, Object> doc, Map<String, Object> data, String qrBase64) {
        Map<String, String> vars = new LinkedHashMap<>();

        for (Map.Entry<String, Object> e : data.entrySet()) {
            vars.put(e.getKey(), value(e.getValue(), "-"));
        }

        String code = value(doc.get("short_code"), "-");

        vars.put("organizationNo", value(doc.get("organization_no"), "-"));
        vars.put("organizationName", value(doc.get("organization_name"), "-"));
        vars.put("documentType", value(doc.get("document_type"), "-"));
        vars.put("internalDocumentNo", value(doc.get("internal_document_no"), "-"));
        vars.put("shortCode", code);
        vars.put("verificationUrl", "https://khaier.sa/" + code);
        vars.put("qrBase64", qrBase64);
        vars.put("statusText", "الوثيقة صحيحة وفعالة");

        vars.putIfAbsent("receiptNo", value(doc.get("internal_document_no"), "-"));
        vars.putIfAbsent("donorName", "-");
        vars.putIfAbsent("amount", "-");
        vars.putIfAbsent("donationType", "-");
        vars.putIfAbsent("paymentMethod", "-");
        vars.putIfAbsent("receiptDate", "-");

        return vars;
    }

    private String renderTemplate(String html, Map<String, String> vars) {
        String result = html;

        for (Map.Entry<String, String> e : vars.entrySet()) {
            String value = "qrBase64".equals(e.getKey()) ? e.getValue() : safe(e.getValue());
            result = result.replace("{{" + e.getKey() + "}}", value);
            result = result.replace("{{ " + e.getKey() + " }}", value);
        }

        return result;
    }

    private byte[] renderPdf(String html, String code) throws Exception {
        Path htmlFile = Files.createTempFile("receipt-" + code + "-", ".html");
        Path pdfFile = Files.createTempFile("receipt-" + code + "-", ".pdf");

        try {
            Files.writeString(htmlFile, html, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                    "wkhtmltopdf",
                    "--encoding", "utf-8",
                    "--page-size", "A4",
                    "--margin-top", "10mm",
                    "--margin-right", "10mm",
                    "--margin-bottom", "10mm",
                    "--margin-left", "10mm",
                    "--disable-smart-shrinking",
                    htmlFile.toAbsolutePath().toString(),
                    pdfFile.toAbsolutePath().toString()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("PDF generation timeout");
            }

            if (process.exitValue() != 0) {
                throw new IllegalStateException("wkhtmltopdf failed: " + output);
            }

            return Files.readAllBytes(pdfFile);
        } finally {
            Files.deleteIfExists(htmlFile);
            Files.deleteIfExists(pdfFile);
        }
    }

    private String fallbackReceiptHtml(Map<String, String> vars) {
        return "<!doctype html><html lang='ar' dir='rtl'><head><meta charset='utf-8'><title>سند قبض</title>"
                + "<style>body{font-family:Arial,Tahoma;padding:40px}.box{border:2px solid #0f766e;border-radius:16px;padding:28px}"
                + "h1{color:#0f766e}.row{border-bottom:1px solid #ddd;padding:10px}.qr{width:120px}</style></head><body>"
                + "<div class='box'><h1>سند قبض</h1>"
                + "<div class='row'>اسم الجمعية: " + safe(vars.get("organizationName")) + "</div>"
                + "<div class='row'>رقم السند: " + safe(vars.get("receiptNo")) + "</div>"
                + "<div class='row'>اسم المتبرع: " + safe(vars.get("donorName")) + "</div>"
                + "<div class='row'>المبلغ: " + safe(vars.get("amount")) + " ريال</div>"
                + "<div class='row'>كود التحقق: " + safe(vars.get("shortCode")) + "</div>"
                + "<p><img class='qr' src='" + vars.get("qrBase64") + "'></p>"
                + "</div></body></html>";
    }

    private String qrDataUri(String text) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, 240, 240);

        BufferedImage image = new BufferedImage(240, 240, BufferedImage.TYPE_INT_RGB);

        for (int x = 0; x < 240; x++) {
            for (int y = 0; y < 240; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0xFF111827 : 0xFFFFFFFF);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private ResponseEntity<byte[]> htmlError(String message) {
        String html = "<html lang='ar' dir='rtl'><head><meta charset='utf-8'></head>"
                + "<body style='font-family:Arial;text-align:center;padding:60px'><h2>"
                + safe(message)
                + "</h2></body></html>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);

        return new ResponseEntity<>(html.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
    }

    private String value(Object value, Object fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback == null ? "-" : String.valueOf(fallback);
        }
        return String.valueOf(value);
    }

    private String safe(Object value) {
        if (value == null) return "-";
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
