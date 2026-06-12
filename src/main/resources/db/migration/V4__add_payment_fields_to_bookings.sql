-- Migração para adicionar campos de pacotes e meio de pagamento à tabela de agendamentos
ALTER TABLE bookings ADD COLUMN customer_package_id UUID;
ALTER TABLE bookings ADD COLUMN payment_method VARCHAR(50) NOT NULL DEFAULT 'CARD';
