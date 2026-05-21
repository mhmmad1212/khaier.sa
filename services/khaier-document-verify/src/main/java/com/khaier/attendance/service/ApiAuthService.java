package com.khaier.attendance.service;

import com.khaier.attendance.utils.CryptoUtils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.Duration;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Service
public class ApiAuthService {

    private final JdbcTemplate jdbcTemplate;

    public ApiAuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String verify(HttpServletRequest request, String rawBody) {
        String clientId = header(request, "X-Client-Id");
        String timestamp = header(request, "X-Timestamp");
        String nonce = header(request, "X-Nonce");
        String signature = header(request, "X-Signature");

        if (clientId == null || timestamp == null || nonce == null || signature == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication headers");
        }

        Instant requestTime;
        try {
            requestTime = Instant.parse(timestamp);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid timestamp");
        }

        long diffSeconds = Math.abs(Duration.between(requestTime, Instant.now()).toSeconds());
        if (diffSeconds > 300) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired timestamp");
        }

        List<Map<String, Object>> clients = jdbcTemplate.queryForList("""
                SELECT client_id, secret_value, status
                FROM api_clients
                WHERE client_id = ?
                LIMIT 1
                """, clientId);

        if (clients.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid client");
        }

        Map<String, Object> client = clients.get(0);

        if (!"active".equals(String.valueOf(client.get("status")))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inactive client");
        }

        String secret = String.valueOf(client.get("secret_value"));
        String bodyHash = CryptoUtils.sha256Hex(rawBody == null ? "" : rawBody);
        String canonical = request.getMethod() + "\n"
                + request.getRequestURI() + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + bodyHash;

        String expected = CryptoUtils.hmacSha256Hex(secret, canonical);

        if (!CryptoUtils.secureEquals(expected, signature.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
        }

        jdbcTemplate.update("DELETE FROM api_request_nonces WHERE expires_at < NOW()");

        try {
            jdbcTemplate.update("""
                    INSERT INTO api_request_nonces (client_id, nonce, expires_at)
                    VALUES (?, ?, ?)
                    """, clientId, nonce, Timestamp.from(Instant.now().plusSeconds(300)));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nonce already used");
        }

        return clientId;
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
