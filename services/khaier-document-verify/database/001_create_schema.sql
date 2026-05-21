-- Khaier Document Verification Service
-- MySQL 8 schema
-- This file creates the main database tables only.
-- It does not insert real secrets.

CREATE DATABASE IF NOT EXISTS document_verify
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE document_verify;

CREATE TABLE IF NOT EXISTS api_clients (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    client_id VARCHAR(100) NOT NULL,
    secret_value VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    allowed_ips JSON NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_clients_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_types (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL,
    name_ar VARCHAR(255) NOT NULL,
    name_en VARCHAR(255) NULL,
    default_security_level VARCHAR(50) NOT NULL DEFAULT 'mobile_match',
    public_display_fields JSON NULL,
    allow_html_template TINYINT(1) NOT NULL DEFAULT 1,
    allow_image_template TINYINT(1) NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_types_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS verified_documents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    short_code VARCHAR(32) NOT NULL,
    organization_no VARCHAR(100) NOT NULL,
    organization_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    internal_document_no VARCHAR(150) NULL,
    reference_system VARCHAR(100) NULL,
    reference_id VARCHAR(150) NULL,
    idempotency_key VARCHAR(255) NULL,
    mobile_hash VARCHAR(128) NULL,
    mobile_last4 VARCHAR(10) NULL,
    document_date DATE NULL,
    document_data JSON NOT NULL,
    document_hash VARCHAR(128) NOT NULL,
    template_id BIGINT UNSIGNED NULL,
    template_version INT NULL,
    pdf_file_path VARCHAR(500) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    revoked_reason VARCHAR(500) NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_verified_documents_short_code (short_code),
    UNIQUE KEY uk_verified_documents_idempotency (idempotency_key),
    KEY idx_verified_documents_org_type (organization_no, document_type),
    KEY idx_verified_documents_internal_no (internal_document_no),
    KEY idx_verified_documents_status (status),
    KEY idx_verified_documents_created_at (created_at),
    CONSTRAINT fk_verified_documents_document_type
        FOREIGN KEY (document_type) REFERENCES document_types(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_templates (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    organization_no VARCHAR(100) NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    template_format VARCHAR(50) NOT NULL DEFAULT 'html',
    template_file_path VARCHAR(500) NULL,
    html_content LONGTEXT NULL,
    field_mapping JSON NULL,
    version_no INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'draft',
    is_active TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_document_templates_org_type (organization_no, document_type),
    KEY idx_document_templates_active (organization_no, document_type, is_active),
    CONSTRAINT fk_document_templates_document_type
        FOREIGN KEY (document_type) REFERENCES document_types(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_template_types (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_id BIGINT UNSIGNED NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_type (template_id, document_type),
    CONSTRAINT fk_document_template_types_template
        FOREIGN KEY (template_id) REFERENCES document_templates(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_document_template_types_document_type
        FOREIGN KEY (document_type) REFERENCES document_types(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_download_tokens (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    document_id BIGINT UNSIGNED NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_download_tokens_token_hash (token_hash),
    KEY idx_download_tokens_document_id (document_id),
    KEY idx_download_tokens_expires_at (expires_at),
    CONSTRAINT fk_download_tokens_document
        FOREIGN KEY (document_id) REFERENCES verified_documents(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_access_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    document_id BIGINT UNSIGNED NULL,
    short_code VARCHAR(32) NULL,
    action VARCHAR(100) NOT NULL,
    ip_address VARCHAR(100) NULL,
    user_agent VARCHAR(1000) NULL,
    details JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_access_logs_document_id (document_id),
    KEY idx_access_logs_short_code (short_code),
    KEY idx_access_logs_action (action),
    KEY idx_access_logs_created_at (created_at),
    CONSTRAINT fk_access_logs_document
        FOREIGN KEY (document_id) REFERENCES verified_documents(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_otp_codes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    document_id BIGINT UNSIGNED NOT NULL,
    mobile_hash VARCHAR(128) NOT NULL,
    otp_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_otp_document_id (document_id),
    KEY idx_otp_expires_at (expires_at),
    CONSTRAINT fk_otp_document
        FOREIGN KEY (document_id) REFERENCES verified_documents(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
