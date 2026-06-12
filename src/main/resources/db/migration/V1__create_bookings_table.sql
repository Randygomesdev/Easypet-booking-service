CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    pet_id UUID NOT NULL,
    partner_id UUID NOT NULL,
    user_id UUID NOT NULL,
    booking_date TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    notes TEXT,
    price NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bookings_pet_id ON bookings(pet_id);
CREATE INDEX idx_bookings_partner_id ON bookings(partner_id);
CREATE INDEX idx_bookings_user_id ON bookings(user_id);
