USE document_verify;

-- هذا الملف مثال فقط.
-- لا تضع Secret حقيقي داخل GitHub.
-- أنشئ Secret من السيرفر باستخدام openssl ثم نفذ INSERT يدويًا.

-- Example:
-- SET @client_id = 'client-1001';
-- SET @client_name = 'Association 1001';
-- SET @secret_value = 'REPLACE_WITH_GENERATED_SECRET';

-- INSERT INTO api_clients
-- (client_id, secret_value, name, allowed_ips, status)
-- VALUES
-- (@client_id, @secret_value, @client_name, JSON_ARRAY(), 'active')
-- ON DUPLICATE KEY UPDATE
--     secret_value = VALUES(secret_value),
--     name = VALUES(name),
--     allowed_ips = VALUES(allowed_ips),
--     status = 'active';
