ALTER TABLE bookings ADD COLUMN deleted_at TIMESTAMP WITHOUT TIME ZONE;

CREATE INDEX idx_bookings_deleted_at ON bookings(deleted_at);
