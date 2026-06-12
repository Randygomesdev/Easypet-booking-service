package br.com.easypet.booking.domain.entity;

import br.com.easypet.booking.domain.enums.BookingStatus;
import br.com.easypet.booking.domain.enums.BookingType;
import br.com.easypet.booking.domain.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@SQLDelete(sql = "UPDATE bookings SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    private UUID id;

    @Column(name = "pet_id", nullable = false)
    private UUID petId;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "booking_date", nullable = false)
    private LocalDateTime bookingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private BookingType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    @Column(name = "notes")
    private String notes;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "check_in")
    private LocalDateTime checkIn;

    @Column(name = "check_out")
    private LocalDateTime checkOut;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "customer_package_id")
    private UUID customerPackageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.CARD;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "is_fitting_request", nullable = false)
    @Builder.Default
    private Boolean isFittingRequest = false;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (isFittingRequest == null) {
            isFittingRequest = false;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
