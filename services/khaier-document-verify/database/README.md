# Database Migrations

This folder contains the SQL files required to create the database schema for the Khaier Document Verification Service.

Execution order:

1. 001_create_schema.sql
2. 002_seed_document_types.sql

Do not commit real API secrets.

Use this file only as a reference:

003_create_api_client_example.sql

Real client secrets must be generated on the server only.

Database name:

document_verify

Main tables:

- api_clients
- document_types
- verified_documents
- document_templates
- document_template_types
- document_download_tokens
- document_access_logs
- document_otp_codes
