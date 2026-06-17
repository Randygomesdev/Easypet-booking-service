-- Remove bookings duplicados (mesmo staff + horário) mantendo o mais antigo.
-- Necessário para dados de teste criados antes desta constraint existir.
DELETE FROM bookings
WHERE id NOT IN (
    SELECT DISTINCT ON (staff_id, booking_date) id
    FROM bookings
    WHERE status NOT IN ('CANCELLED')
      AND (is_fitting_request IS NULL OR is_fitting_request = false)
    ORDER BY staff_id, booking_date, created_at ASC
)
AND status NOT IN ('CANCELLED')
AND (is_fitting_request IS NULL OR is_fitting_request = false);

-- Impede duplo agendamento do mesmo colaborador no mesmo horário exato.
-- Encaixes (is_fitting_request = true) e cancelados são excluídos da restrição.
CREATE UNIQUE INDEX IF NOT EXISTS uq_staff_booking_date
    ON bookings (staff_id, booking_date)
    WHERE status NOT IN ('CANCELLED')
      AND (is_fitting_request IS NULL OR is_fitting_request = false);
