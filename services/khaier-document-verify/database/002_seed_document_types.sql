USE document_verify;

INSERT INTO document_types
(code, name_ar, name_en, default_security_level, public_display_fields, allow_html_template, allow_image_template, is_active)
VALUES
('receipt_voucher', 'سند قبض', 'Receipt Voucher', 'mobile_match',
 JSON_ARRAY('receiptNo', 'donorName', 'amount', 'donationType', 'paymentMethod', 'receiptDate'), 1, 1, 1),

('payment_voucher', 'سند صرف', 'Payment Voucher', 'mobile_match',
 JSON_ARRAY('paymentNo', 'beneficiaryName', 'amount', 'paymentMethod', 'paymentDate'), 1, 1, 1),

('salary_definition', 'تعريف راتب', 'Salary Definition', 'otp',
 JSON_ARRAY('employeeName', 'jobTitle', 'totalSalary', 'netSalary', 'documentDate'), 1, 1, 1),

('beneficiary_statement', 'تعريف مستفيد', 'Beneficiary Statement', 'otp',
 JSON_ARRAY('beneficiaryName', 'beneficiaryNo', 'category', 'status'), 1, 1, 1),

('beneficiary_card', 'بطاقة مستفيد', 'Beneficiary Card', 'otp',
 JSON_ARRAY('beneficiaryName', 'beneficiaryNo', 'category', 'status'), 1, 1, 1),

('official_letter', 'خطاب رسمي', 'Official Letter', 'mobile_match',
 JSON_ARRAY('letterNo', 'letterTitle', 'letterDate'), 1, 1, 1),

('board_minutes', 'محضر مجلس إدارة', 'Board Minutes', 'restricted',
 JSON_ARRAY('meetingNo', 'meetingDate', 'meetingType'), 1, 0, 1),

('certificate', 'شهادة', 'Certificate', 'none',
 JSON_ARRAY('certificateNo', 'recipientName', 'certificateDate'), 1, 1, 1)
ON DUPLICATE KEY UPDATE
    name_ar = VALUES(name_ar),
    name_en = VALUES(name_en),
    default_security_level = VALUES(default_security_level),
    public_display_fields = VALUES(public_display_fields),
    allow_html_template = VALUES(allow_html_template),
    allow_image_template = VALUES(allow_image_template),
    is_active = VALUES(is_active);
