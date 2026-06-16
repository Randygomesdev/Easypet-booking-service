-- Impede duplo agendamento do mesmo colaborador no mesmo horário exato.
-- Encaixes (is_fitting_request = true) e cancelados são excluídos da restrição.
CREATE UNIQUE INDEX IF NOT EXISTS uq_staff_booking_date
    ON bookings (staff_id, booking_date)
    WHERE status NOT IN ('CANCELLED')
      AND (is_fitting_request IS NULL OR is_fitting_request = false);
