-- Migração para adicionar campos de hospedagem (check-in, check-out e service_id) à tabela de agendamentos

ALTER TABLE bookings ADD COLUMN check_in TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE bookings ADD COLUMN check_out TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE bookings ADD COLUMN service_id UUID;

-- Criação de índices para otimização de busca de sobreposição (overlap) e integridade
CREATE INDEX idx_bookings_check_in ON bookings(check_in);
CREATE INDEX idx_bookings_check_out ON bookings(check_out);
CREATE INDEX idx_bookings_service_id ON bookings(service_id);
