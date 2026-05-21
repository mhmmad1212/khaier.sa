package com.khaier.document.controller;

import com.khaier.document.service.ApiAuthService;
import com.khaier.document.utils.CryptoUtils;
import com.khaier.document.utils.MobileUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

@RestController
public class QrCodeController {

    private final JdbcTemplate jdbcTemplate;

    public QrCodeController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping(value = "/qr/{shortCode:[A-Za-z0-9]{6,20}}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable String shortCode) throws Exception {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verified_documents WHERE short_code = ?",
                Integer.class,
                shortCode
        );

        if (count == null || count == 0) {
            return ResponseEntity.notFound().build();
        }

        String url = "https://khaier.sa/" + shortCode;

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, 280, 280);

        BufferedImage image = new BufferedImage(280, 280, BufferedImage.TYPE_INT_RGB);

        for (int x = 0; x < 280; x++) {
            for (int y = 0; y < 280; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0xFF111827 : 0xFFFFFFFF);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noCache());

        return new ResponseEntity<>(out.toByteArray(), headers, HttpStatus.OK);
    }
}
