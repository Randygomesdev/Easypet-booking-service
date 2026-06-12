package br.com.easypet.booking.repository;

import br.com.easypet.booking.domain.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Booking> {
    
    Page<Booking> findByUserId(UUID userId, Pageable pageable);
    
    Page<Booking> findByPartnerId(UUID partnerId, Pageable pageable);
    
    Page<Booking> findByPartnerIdAndStaffId(UUID partnerId, UUID staffId, Pageable pageable);
    
    Page<Booking> findByPartnerIdAndBookingDateBetween(
        UUID partnerId, java.time.LocalDateTime start, java.time.LocalDateTime end, Pageable pageable
    );
    
    Page<Booking> findByPartnerIdAndStaffIdAndBookingDateBetween(
        UUID partnerId, UUID staffId, java.time.LocalDateTime start, java.time.LocalDateTime end, Pageable pageable
    );
    
    Page<Booking> findByPetId(UUID petId, Pageable pageable);

    java.util.List<Booking> findByPartnerIdAndStatusNotAndBookingDateBetween(
        UUID partnerId,
        br.com.easypet.booking.domain.enums.BookingStatus status,
        java.time.LocalDateTime start,
        java.time.LocalDateTime end
    );

    java.util.List<Booking> findByPartnerIdAndStaffIdAndStatusNotAndBookingDateBetween(
        UUID partnerId,
        UUID staffId,
        br.com.easypet.booking.domain.enums.BookingStatus status,
        java.time.LocalDateTime start,
        java.time.LocalDateTime end
    );

    java.util.List<Booking> findByPartnerIdAndStatusNotAndBookingDateBetweenAndStaffIdNotNull(
        UUID partnerId,
        br.com.easypet.booking.domain.enums.BookingStatus status,
        java.time.LocalDateTime start,
        java.time.LocalDateTime end
    );

    @org.springframework.data.jpa.repository.Query("SELECT b FROM Booking b WHERE b.partnerId = :partnerId " +
            "AND b.type = br.com.easypet.booking.domain.enums.BookingType.BOARDING " +
            "AND b.status != br.com.easypet.booking.domain.enums.BookingStatus.CANCELLED " +
            "AND b.checkIn < :checkOut AND b.checkOut > :checkIn")
    java.util.List<Booking> findOverlappingBoardings(
            @org.springframework.data.repository.query.Param("partnerId") UUID partnerId,
            @org.springframework.data.repository.query.Param("checkIn") java.time.LocalDateTime checkIn,
            @org.springframework.data.repository.query.Param("checkOut") java.time.LocalDateTime checkOut);

    // ── Analytics ────────────────────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(b.price), 0) FROM Booking b " +
           "WHERE b.partnerId = :partnerId " +
           "AND b.status = br.com.easypet.booking.domain.enums.BookingStatus.COMPLETED " +
           "AND b.bookingDate >= :start AND b.bookingDate < :end " +
           "AND b.deletedAt IS NULL")
    BigDecimal sumRevenueByPartnerAndPeriod(
            @Param("partnerId") UUID partnerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = "SELECT booking_date::date AS day, COUNT(*) AS total " +
                   "FROM bookings " +
                   "WHERE partner_id = :partnerId " +
                   "AND booking_date >= :startDate " +
                   "AND deleted_at IS NULL " +
                   "GROUP BY booking_date::date " +
                   "ORDER BY day",
           nativeQuery = true)
    List<Object[]> countDailyBookings(
            @Param("partnerId") UUID partnerId,
            @Param("startDate") LocalDateTime startDate);

    @Query("SELECT COUNT(DISTINCT b.userId) FROM Booking b " +
           "WHERE b.partnerId = :partnerId " +
           "AND b.deletedAt IS NULL " +
           "AND b.bookingDate >= :start AND b.bookingDate < :end " +
           "AND NOT EXISTS (" +
           "   SELECT 1 FROM Booking b2 " +
           "   WHERE b2.userId = b.userId " +
           "   AND b2.partnerId = :partnerId " +
           "   AND b2.deletedAt IS NULL " +
           "   AND b2.bookingDate < :start" +
           ")")
    Long countNewClientsByPartnerAndPeriod(
            @Param("partnerId") UUID partnerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
