package br.com.easypet.booking.service;

import br.com.easypet.booking.domain.entity.Booking;
import br.com.easypet.booking.domain.enums.BookingStatus;
import br.com.easypet.booking.dto.request.BookingRequest;
import br.com.easypet.booking.dto.request.FittingDecisionRequest;
import br.com.easypet.booking.dto.response.BookingResponse;
import br.com.easypet.booking.dto.response.FittingDecisionResponse;
import br.com.easypet.booking.mapper.BookingMapper;
import br.com.easypet.booking.repository.BookingRepository;
import br.com.easypet.booking.exception.ResourceNotFoundException;
import br.com.easypet.booking.exception.BusinessException;
import br.com.easypet.booking.dto.response.AvailabilitySlot;
import br.com.easypet.booking.dto.response.PartnerIntegrationResponse;
import br.com.easypet.booking.dto.response.PartnerBusinessHourIntegrationResponse;
import br.com.easypet.booking.client.PartnerServiceClient;
import br.com.easypet.booking.client.PaymentServiceClient;
import br.com.easypet.booking.client.dto.CreditConsumeResponse;
import br.com.easypet.booking.domain.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.easypet.booking.dto.response.ClientStatsResponse;
import br.com.easypet.booking.dto.response.DailyStatsEntry;
import br.com.easypet.booking.dto.response.RevenueStatsResponse;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final PartnerIntegrationService partnerIntegrationService;
    private final PartnerServiceClient partnerServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    public BookingResponse createBooking(BookingRequest request) {
        log.info("Iniciando criação de agendamento do tipo {} para o pet {}", request.type(), request.petId());
        Booking booking = bookingMapper.toEntity(request);
        if (booking.getIsFittingRequest() == null) {
            booking.setIsFittingRequest(false);
        }

        // Atribuir o ID do usuário atualmente autenticado extraído do JWT
        UUID currentUserId = getCurrentUserId();
        booking.setUserId(currentUserId);

        // Interceptar se o pagamento for via crédito de pacote pré-pago
        if (request.paymentMethod() == PaymentMethod.PACKAGE_CREDIT) {
            log.info("Processando pagamento via crédito de pacote para o usuário: {}", currentUserId);
            if (request.serviceId() == null) {
                throw new BusinessException("O ID do serviço é obrigatório para agendamento com créditos de pacote.");
            }
            
            // Chamar síncronamente o payment-service para debitar 1 crédito do pacote
            CreditConsumeResponse consumeResponse = paymentServiceClient.consumeCredit(
                    currentUserId, request.partnerId(), request.serviceId());
            
            if (consumeResponse != null && consumeResponse.success()) {
                booking.setCustomerPackageId(consumeResponse.customerPackageId());
                booking.setPaymentMethod(PaymentMethod.PACKAGE_CREDIT);
                booking.setStatus(BookingStatus.CONFIRMED); // Agendamento pré-pago é CONFIRMED direto
                booking.setPrice(java.math.BigDecimal.ZERO); // Preço final do agendamento é zero para o checkout
                log.info("Débito realizado com sucesso. Agendamento CONFIRMED via pacote ID: {}", consumeResponse.customerPackageId());
            } else {
                throw new BusinessException("Não foi possível debitar os créditos do seu pacote. Verifique seu saldo.");
            }
        } else {
            // Estado inicial padrão para pagamentos avulsos
            booking.setStatus(BookingStatus.PENDING);
            booking.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod() : PaymentMethod.CARD);
        }

        if (request.type() == br.com.easypet.booking.domain.enums.BookingType.BOARDING) {
            log.info("Validando agendamento de hospedagem/creche...");
            if (request.checkIn() == null || request.checkOut() == null) {
                throw new BusinessException("As datas de check-in e check-out são obrigatórias para hospedagem.");
            }
            if (!request.checkOut().isAfter(request.checkIn())) {
                throw new BusinessException("A data de check-out deve ser posterior à data de check-in.");
            }
            if (request.serviceId() == null) {
                throw new BusinessException("O serviço é obrigatório para hospedagem.");
            }

            // Buscar dados do parceiro e seus serviços
            var partnerDto = partnerServiceClient.getPartnerById(request.partnerId());
            if (partnerDto == null) {
                throw new ResourceNotFoundException("Parceiro não encontrado.");
            }

            // Encontrar o serviço
            var serviceDto = partnerDto.services().stream()
                    .filter(s -> s.id().equals(request.serviceId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado no parceiro selecionado."));

            if (serviceDto.billingUnit() != br.com.easypet.booking.domain.enums.BillingUnit.DAILY) {
                throw new BusinessException("O serviço selecionado deve ser do tipo diário (DAILY) para hospedagem.");
            }

            // Calcular diárias e preço total
            long nights = java.time.temporal.ChronoUnit.DAYS.between(request.checkIn().toLocalDate(), request.checkOut().toLocalDate());
            if (nights <= 0) {
                nights = 1; // Creches/Hospedagens de 1 dia duram pelo menos 1 dia
            }
            java.math.BigDecimal calculatedPrice = serviceDto.price().multiply(java.math.BigDecimal.valueOf(nights));
            booking.setPrice(calculatedPrice);

            // Definir bookingDate igual ao checkIn para compatibilidade com o banco
            booking.setBookingDate(request.checkIn());

            // Validar capacidade com base na regra de sobreposição (overlap)
            int capacity = partnerDto.boardingCapacity() != null ? partnerDto.boardingCapacity() : 0;
            log.info("Capacidade máxima do parceiro '{}' para hospedagem: {}", partnerDto.name(), capacity);

            java.util.List<Booking> activeOverlapping = bookingRepository.findOverlappingBoardings(
                    request.partnerId(), request.checkIn(), request.checkOut());

            // Regra fina de validação de capacidade por dia
            java.time.LocalDate startDate = request.checkIn().toLocalDate();
            java.time.LocalDate endDate = request.checkOut().toLocalDate();
            
            for (java.time.LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                final java.time.LocalDate currentDate = date;
                long countOnCurrentDay = activeOverlapping.stream()
                        .filter(b -> {
                            java.time.LocalDate bIn = b.getCheckIn().toLocalDate();
                            java.time.LocalDate bOut = b.getCheckOut().toLocalDate();
                            return !currentDate.isBefore(bIn) && !currentDate.isAfter(bOut);
                        })
                        .count();
                
                if (countOnCurrentDay >= capacity) {
                    log.warn("Capacidade esgotada no dia {}: {}/{} ocupados", currentDate, countOnCurrentDay, capacity);
                    throw new BusinessException("Capacidade de hospedagem esgotada para o dia " + currentDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
                }
            }
            log.info("Validação de capacidade de hospedagem concluída com sucesso.");
        } else {
            // Se for serviço convencional, exige bookingDate
            if (request.bookingDate() == null) {
                throw new BusinessException("A data do agendamento é obrigatória.");
            }
            if (request.serviceId() == null) {
                throw new BusinessException("O serviço é obrigatório para agendamento convencional.");
            }
            
            // Lógica de Alocação de Equipe e Capacidade
            UUID partnerId = request.partnerId();
            UUID serviceId = request.serviceId();
            LocalDateTime bookingDate = request.bookingDate();
            boolean isFitting = request.requestFitting() != null && request.requestFitting();
            
            // Busca todos os profissionais ativos habilitados para o serviço
            List<br.com.easypet.booking.client.dto.StaffResponseDto> staffList = partnerServiceClient.getStaffByPartnerId(partnerId, serviceId);
            
            if (staffList.isEmpty()) {
                throw new BusinessException("Nenhum profissional habilitado cadastrado para este serviço.");
            }
            
            UUID allocatedStaffId = null;
            
            if (request.staffId() != null) {
                // Seleção de Profissional Específico
                UUID chosenStaffId = request.staffId();
                var chosenStaff = staffList.stream()
                        .filter(s -> s.id().equals(chosenStaffId))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("O profissional selecionado não está habilitado para este serviço."));
                        
                // Verificar Ausência e Escala
                boolean absent = isStaffAbsent(chosenStaffId, bookingDate);
                ScheduleStatus schedStatus = getStaffScheduleStatus(chosenStaffId, bookingDate);
                boolean inSchedule = schedStatus == ScheduleStatus.AVAILABLE;
                boolean inLunch    = schedStatus == ScheduleStatus.LUNCH_BREAK;
                boolean conflict = inSchedule && hasStaffConflict(partnerId, chosenStaffId, bookingDate);

                if (absent || !inSchedule || conflict) {
                    if (isFitting) {
                        allocatedStaffId = chosenStaffId;
                        booking.setIsFittingRequest(true);
                        booking.setStatus(BookingStatus.PENDING);
                    } else {
                        String msg = absent   ? "O profissional selecionado está ausente neste horário." :
                                     inLunch  ? "O profissional está em horário de almoço. Solicite um encaixe." :
                                     !inSchedule ? "Este horário está fora do expediente regular do profissional." :
                                     "O profissional selecionado já possui outro agendamento neste horário.";
                        throw new BusinessException(msg);
                    }
                } else {
                    allocatedStaffId = chosenStaffId;
                }
            } else {
                // Alocação Automática - "Qualquer Profissional" (Round-Robin)
                // 1. Filtrar profissionais que não estão ausentes e estão dentro do expediente
                List<br.com.easypet.booking.client.dto.StaffResponseDto> availableStaff = staffList.stream()
                        .filter(s -> !isStaffAbsent(s.id(), bookingDate) && isWithinStaffSchedule(s.id(), bookingDate))
                        .collect(Collectors.toList());
                        
                if (availableStaff.isEmpty()) {
                    if (isFitting) {
                        // Aloca o primeiro da lista geral e marca como encaixe
                        allocatedStaffId = staffList.get(0).id();
                        booking.setIsFittingRequest(true);
                        booking.setStatus(BookingStatus.PENDING);
                    } else {
                        throw new BusinessException("Nenhum profissional está disponível neste horário.");
                    }
                } else {
                    // 2. Buscar contagem de agendamentos no dia para balanceamento Round-Robin
                    LocalDateTime startOfDay = bookingDate.toLocalDate().atStartOfDay();
                    LocalDateTime endOfDay = bookingDate.toLocalDate().atTime(23, 59, 59);
                    List<Booking> dayBookings = bookingRepository.findByPartnerIdAndStatusNotAndBookingDateBetweenAndStaffIdNotNull(
                            partnerId, BookingStatus.CANCELLED, startOfDay, endOfDay);
                            
                    Map<UUID, Long> bookingCounts = dayBookings.stream()
                            .filter(b -> b.getStaffId() != null)
                            .collect(Collectors.groupingBy(Booking::getStaffId, Collectors.counting()));
                            
                    // 3. Escolher o profissional com a menor contagem de agendamentos no dia
                    br.com.easypet.booking.client.dto.StaffResponseDto selectedStaff = availableStaff.stream()
                            .min(Comparator.comparing((br.com.easypet.booking.client.dto.StaffResponseDto s) -> bookingCounts.getOrDefault(s.id(), 0L))
                                    .thenComparing(s -> s.id().toString()))
                            .orElse(availableStaff.get(0));
                            
                    // 4. Verificar conflito de horário para o profissional selecionado
                    if (hasStaffConflict(partnerId, selectedStaff.id(), bookingDate)) {
                        if (isFitting) {
                            allocatedStaffId = selectedStaff.id();
                            booking.setIsFittingRequest(true);
                            booking.setStatus(BookingStatus.PENDING);
                        } else {
                            throw new BusinessException("Todos os profissionais disponíveis estão ocupados neste horário.");
                        }
                    } else {
                        allocatedStaffId = selectedStaff.id();
                    }
                }
            }
            
            booking.setStaffId(allocatedStaffId);
        }

        Booking savedBooking = bookingRepository.save(booking);
        log.info("Agendamento criado com sucesso! ID: {}", savedBooking.getId());
        return bookingMapper.toResponse(savedBooking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(UUID id) {
        log.info("Buscando agendamento por ID: {}", id);
        Booking booking = findBookingOrThrow(id);
        validateOwnership(booking);
        return bookingMapper.toResponse(booking);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookingsByUserId(UUID userId, Pageable pageable) {
        log.info("Buscando agendamentos do usuário {}", userId);
        Page<Booking> bookings = bookingRepository.findByUserId(userId, pageable);
        return bookings.map(bookingMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookingsByPartnerId(UUID partnerId, Pageable pageable) {
        return getBookingsByPartnerId(partnerId, null, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookingsByPartnerId(UUID partnerId, LocalDate date, UUID staffId, Pageable pageable) {
        return getBookingsByPartnerId(partnerId, date, staffId, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookingsByPartnerId(
            UUID partnerId, LocalDate date, UUID staffId,
            BookingStatus status, br.com.easypet.booking.domain.enums.BookingType type,
            LocalDate startDate, LocalDate endDate, Pageable pageable) {
        log.info("Buscando agendamentos do parceiro {}, date: {}, staffId: {}, status: {}, type: {}", partnerId, date, staffId, status, type);

        LocalDateTime start = startDate != null ? startDate.atStartOfDay()
                : date != null ? date.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX)
                : date != null ? date.atTime(LocalTime.MAX) : null;

        final LocalDateTime effectiveStart = start;
        final LocalDateTime effectiveEnd   = end;

        Specification<Booking> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("partnerId"), partnerId));
            if (staffId        != null) predicates.add(cb.equal(root.get("staffId"),    staffId));
            if (status         != null) predicates.add(cb.equal(root.get("status"),     status));
            if (type           != null) predicates.add(cb.equal(root.get("type"),       type));
            if (effectiveStart != null) predicates.add(cb.greaterThanOrEqualTo(root.get("bookingDate"), effectiveStart));
            if (effectiveEnd   != null) predicates.add(cb.lessThanOrEqualTo(root.get("bookingDate"),   effectiveEnd));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return bookingRepository.findAll(spec, pageable).map(bookingMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookingsByPetId(UUID petId, Pageable pageable) {
        log.info("Buscando agendamentos do pet {}", petId);
        Page<Booking> bookings = bookingRepository.findByPetId(petId, pageable);
        return bookings.map(bookingMapper::toResponse);
    }

    public BookingResponse updateBooking(UUID id, BookingRequest request) {
        log.info("Atualizando dados do agendamento ID: {}", id);
        Booking booking = findBookingOrThrow(id);
        validateOwnership(booking);

        bookingMapper.updateEntityFromRequest(request, booking);
        Booking updatedBooking = bookingRepository.save(booking);
        log.info("Agendamento ID: {} atualizado com sucesso!", id);
        return bookingMapper.toResponse(updatedBooking);
    }

    public BookingResponse updateStatus(UUID id, BookingStatus status) {
        log.info("Alterando status do agendamento ID: {} para {}", id, status);
        Booking booking = findBookingOrThrow(id);
        validateOwnership(booking);

        booking.setStatus(status);
        Booking updatedBooking = bookingRepository.save(booking);
        log.info("Status do agendamento ID: {} alterado para {}", id, status);
        return bookingMapper.toResponse(updatedBooking);
    }

    public void deleteBooking(UUID id) {
        log.info("Recebida requisição para excluir agendamento ID: {}", id);
        Booking booking = findBookingOrThrow(id);
        validateOwnership(booking);
        bookingRepository.deleteById(id);
        log.info("Agendamento ID: {} excluído com sucesso (Soft Delete efetuado)", id);
    }

    // ─── Métodos auxiliares ───────────────────────────────────────────────────

    /**
     * Busca o agendamento pelo ID ou lança ResourceNotFoundException.
     */
    private Booking findBookingOrThrow(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Agendamento não encontrado com o ID: {}", id);
                    return new ResourceNotFoundException("Agendamento não encontrado com o ID: " + id);
                });
    }

    /**
     * Verifica se o agendamento pertence ao usuário autenticado.
     * Lança BusinessException (403) caso o usuário não seja o dono.
     */
    private void validateOwnership(Booking booking) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = UUID.fromString((String) auth.getCredentials());
        
        boolean isAdminOrPartner = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_PARTNER"));

        if (!booking.getUserId().equals(currentUserId) && !isAdminOrPartner) {
            log.warn("Tentativa de acesso não autorizado ao agendamento ID: {} pelo usuário: {}",
                    booking.getId(), currentUserId);
            throw new BusinessException("Você não tem permissão para acessar este agendamento.");
        }
    }

    /**
     * Extrai o ID do usuário autenticado do SecurityContext (injetado pelo JwtAuthenticationFilter).
     */
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString((String) authentication.getCredentials());
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlot> getAvailability(UUID partnerId, UUID serviceId, LocalDate date) {
        log.info("Calculando slots de disponibilidade para o parceiro {} e serviço {} na data {}", partnerId, serviceId, date);
        
        // Se serviceId for nulo, mantemos o comportamento legado
        if (serviceId == null) {
            return getLegacyAvailability(partnerId, date);
        }

        // 1. Obter horários do parceiro
        PartnerIntegrationResponse partner = partnerIntegrationService.getPartnerDetails(partnerId);
        
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        PartnerBusinessHourIntegrationResponse todayHour = null;
        if (partner.businessHours() != null) {
            for (PartnerBusinessHourIntegrationResponse bh : partner.businessHours()) {
                if (bh.dayOfWeek() == dayOfWeek) {
                    todayHour = bh;
                    break;
                }
            }
        }
        
        if (todayHour == null || todayHour.closed()) {
            log.info("Parceiro {} está FECHADO na data {} ({})", partnerId, date, dayOfWeek);
            return new ArrayList<>();
        }
        
        String startHourStr = todayHour.businessStartHour() != null && !todayHour.businessStartHour().trim().isEmpty()
            ? todayHour.businessStartHour().trim() : "08:00";
        String endHourStr = todayHour.businessEndHour() != null && !todayHour.businessEndHour().trim().isEmpty()
            ? todayHour.businessEndHour().trim() : "18:00";
        
        if (startHourStr.length() == 4 && startHourStr.contains(":")) {
            startHourStr = "0" + startHourStr;
        }
        if (endHourStr.length() == 4 && endHourStr.contains(":")) {
            endHourStr = "0" + endHourStr;
        }
        
        LocalTime businessStart;
        LocalTime businessEnd;
        
        try {
            businessStart = LocalTime.parse(startHourStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            log.error("Erro ao parsear businessStartHour '{}' para o dia {}, usando 08:00 como fallback: {}", startHourStr, dayOfWeek, e.getMessage());
            businessStart = LocalTime.of(8, 0);
        }
        
        try {
            businessEnd = LocalTime.parse(endHourStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            log.error("Erro ao parsear businessEndHour '{}' para o dia {}, usando 18:00 como fallback: {}", endHourStr, dayOfWeek, e.getMessage());
            businessEnd = LocalTime.of(18, 0);
        }
        
        if (businessStart.isAfter(businessEnd) || businessStart.equals(businessEnd)) {
            log.warn("Horários de funcionamento inválidos para o dia {}, utilizando fallback de 08:00 as 18:00", dayOfWeek);
            businessStart = LocalTime.of(8, 0);
            businessEnd = LocalTime.of(18, 0);
        }
        
        LocalTime lunchStart = null;
        LocalTime lunchEnd = null;
        if (todayHour.lunchStartHour() != null && !todayHour.lunchStartHour().trim().isEmpty() &&
            todayHour.lunchEndHour() != null && !todayHour.lunchEndHour().trim().isEmpty()) {
            try {
                String lStartStr = todayHour.lunchStartHour().trim();
                String lEndStr = todayHour.lunchEndHour().trim();
                if (lStartStr.length() == 4 && lStartStr.contains(":")) lStartStr = "0" + lStartStr;
                if (lEndStr.length() == 4 && lEndStr.contains(":")) lEndStr = "0" + lEndStr;
                
                lunchStart = LocalTime.parse(lStartStr, DateTimeFormatter.ofPattern("HH:mm"));
                lunchEnd = LocalTime.parse(lEndStr, DateTimeFormatter.ofPattern("HH:mm"));
                
                if (lunchStart.isAfter(lunchEnd) || lunchStart.equals(lunchEnd)) {
                    log.warn("Horário de almoço inválido para o dia {}, ignorando intervalo", dayOfWeek);
                    lunchStart = null;
                    lunchEnd = null;
                }
            } catch (Exception e) {
                log.error("Erro ao parsear horário de almoço do parceiro para o dia {}: {}", dayOfWeek, e.getMessage());
            }
        }

        // 2. Obter profissionais habilitados
        List<br.com.easypet.booking.client.dto.StaffResponseDto> staffList = partnerServiceClient.getStaffByPartnerId(partnerId, serviceId);
        if (staffList.isEmpty()) {
            log.info("Nenhum profissional habilitado encontrado para o serviço {} no parceiro {}", serviceId, partnerId);
            return new ArrayList<>();
        }
        
        // 3. Gerar slots de 30 minutos
        List<AvailabilitySlot> slots = new ArrayList<>();
        LocalTime current = businessStart;
        LocalTime nowTime = LocalTime.now();
        LocalDate today = LocalDate.now();
        
        while (current.plusMinutes(30).isBefore(businessEnd) || current.plusMinutes(30).equals(businessEnd)) {
            LocalTime slotTime = current;
            current = current.plusMinutes(30);
            
            // Se cair no horário de almoço, ignoramos (omitimos o slot)
            if (lunchStart != null && lunchEnd != null) {
                if ((slotTime.equals(lunchStart) || slotTime.isAfter(lunchStart)) && slotTime.isBefore(lunchEnd)) {
                    continue;
                }
            }
            
            LocalDateTime slotDateTime = LocalDateTime.of(date, slotTime);
            String timeStr = slotTime.format(DateTimeFormatter.ofPattern("HH:mm"));

            // Se for hoje, impede agendamento em horário retroativo
            if (date.equals(today) && slotTime.isBefore(nowTime)) {
                slots.add(new AvailabilitySlot(timeStr, false, List.of(), "OUT_OF_SCHEDULE", false));
                continue;
            }

            List<br.com.easypet.booking.client.dto.StaffResponseDto> availableStaff = new ArrayList<>();
            boolean anyAbsent = false;
            boolean anyScheduleMatch = false;
            boolean anyConflict = false;
            boolean anyLunchBreak = false;

            for (var staff : staffList) {
                boolean absent = isStaffAbsent(staff.id(), slotDateTime);
                ScheduleStatus schedStatus = getStaffScheduleStatus(staff.id(), slotDateTime);
                boolean inSchedule = schedStatus == ScheduleStatus.AVAILABLE;
                boolean inLunch    = schedStatus == ScheduleStatus.LUNCH_BREAK;
                boolean conflict   = inSchedule && hasStaffConflict(partnerId, staff.id(), slotDateTime);

                if (!absent && inSchedule && !conflict) {
                    availableStaff.add(staff);
                }
                if (absent)   anyAbsent       = true;
                if (inSchedule) anyScheduleMatch = true;
                if (conflict) anyConflict      = true;
                if (inLunch && !absent) anyLunchBreak = true;
            }

            if (!availableStaff.isEmpty()) {
                slots.add(new AvailabilitySlot(timeStr, true, availableStaff, null, null));
            } else {
                String reason;
                if (anyLunchBreak && !anyConflict) {
                    reason = "LUNCH_BREAK";
                } else if (!anyScheduleMatch) {
                    reason = "OUT_OF_SCHEDULE";
                } else if (anyAbsent && !anyConflict) {
                    reason = "BLOCKED_ABSENCE";
                } else if (anyConflict) {
                    reason = "FULLY_BOOKED";
                } else {
                    reason = "UNAVAILABLE";
                }
                slots.add(new AvailabilitySlot(timeStr, false, staffList, reason, true));
            }
        }
        
        return slots;
    }

    @Transactional(readOnly = true)
    private List<AvailabilitySlot> getLegacyAvailability(UUID partnerId, LocalDate date) {
        log.info("Calculando slots de disponibilidade legada para o parceiro {} na data {}", partnerId, date);
        
        // 1. Obter horários do parceiro
        PartnerIntegrationResponse partner = partnerIntegrationService.getPartnerDetails(partnerId);
        
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        PartnerBusinessHourIntegrationResponse todayHour = null;
        if (partner.businessHours() != null) {
            for (PartnerBusinessHourIntegrationResponse bh : partner.businessHours()) {
                if (bh.dayOfWeek() == dayOfWeek) {
                    todayHour = bh;
                    break;
                }
            }
        }
        
        if (todayHour == null || todayHour.closed()) {
            log.info("Parceiro {} está FECHADO na data {} ({})", partnerId, date, dayOfWeek);
            return new ArrayList<>();
        }
        
        String startHourStr = todayHour.businessStartHour() != null && !todayHour.businessStartHour().trim().isEmpty()
            ? todayHour.businessStartHour().trim() : "08:00";
        String endHourStr = todayHour.businessEndHour() != null && !todayHour.businessEndHour().trim().isEmpty()
            ? todayHour.businessEndHour().trim() : "18:00";
        
        if (startHourStr.length() == 4 && startHourStr.contains(":")) {
            startHourStr = "0" + startHourStr;
        }
        if (endHourStr.length() == 4 && endHourStr.contains(":")) {
            endHourStr = "0" + endHourStr;
        }
        
        LocalTime businessStart;
        LocalTime businessEnd;
        
        try {
            businessStart = LocalTime.parse(startHourStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            log.error("Erro ao parsear businessStartHour '{}' para o dia {}, usando 08:00 como fallback: {}", startHourStr, dayOfWeek, e.getMessage());
            businessStart = LocalTime.of(8, 0);
        }
        
        try {
            businessEnd = LocalTime.parse(endHourStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            log.error("Erro ao parsear businessEndHour '{}' para o dia {}, usando 18:00 como fallback: {}", endHourStr, dayOfWeek, e.getMessage());
            businessEnd = LocalTime.of(18, 0);
        }
        
        if (businessStart.isAfter(businessEnd) || businessStart.equals(businessEnd)) {
            log.warn("Horários de funcionamento inválidos para o dia {}, utilizando fallback de 08:00 as 18:00", dayOfWeek);
            businessStart = LocalTime.of(8, 0);
            businessEnd = LocalTime.of(18, 0);
        }
        
        LocalTime lunchStart = null;
        LocalTime lunchEnd = null;
        if (todayHour.lunchStartHour() != null && !todayHour.lunchStartHour().trim().isEmpty() &&
            todayHour.lunchEndHour() != null && !todayHour.lunchEndHour().trim().isEmpty()) {
            try {
                String lStartStr = todayHour.lunchStartHour().trim();
                String lEndStr = todayHour.lunchEndHour().trim();
                if (lStartStr.length() == 4 && lStartStr.contains(":")) lStartStr = "0" + lStartStr;
                if (lEndStr.length() == 4 && lEndStr.contains(":")) lEndStr = "0" + lEndStr;
                
                lunchStart = LocalTime.parse(lStartStr, DateTimeFormatter.ofPattern("HH:mm"));
                lunchEnd = LocalTime.parse(lEndStr, DateTimeFormatter.ofPattern("HH:mm"));
                
                if (lunchStart.isAfter(lunchEnd) || lunchStart.equals(lunchEnd)) {
                    log.warn("Horário de almoço inválido para o dia {}, ignorando intervalo", dayOfWeek);
                    lunchStart = null;
                    lunchEnd = null;
                }
            } catch (Exception e) {
                log.error("Erro ao parsear horário de almoço do parceiro para o dia {}: {}", dayOfWeek, e.getMessage());
            }
        }
        
        // 2. Buscar agendamentos existentes (não cancelados) para o dia
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        List<Booking> bookings = bookingRepository.findByPartnerIdAndStatusNotAndBookingDateBetween(
            partnerId,
            BookingStatus.CANCELLED,
            startOfDay,
            endOfDay
        );
        
        Set<LocalTime> bookedTimes = bookings.stream()
            .map(b -> b.getBookingDate().toLocalTime())
            .collect(Collectors.toSet());
            
        // 3. Gerar slots de 30 minutos
        List<AvailabilitySlot> slots = new ArrayList<>();
        LocalTime current = businessStart;
        LocalTime nowTime = LocalTime.now();
        LocalDate today = LocalDate.now();
        
        while (current.plusMinutes(30).isBefore(businessEnd) || current.plusMinutes(30).equals(businessEnd)) {
            LocalTime slotTime = current;
            current = current.plusMinutes(30);
            
            // Se cair no horário de almoço, ignoramos (omitimos o slot)
            if (lunchStart != null && lunchEnd != null) {
                if ((slotTime.equals(lunchStart) || slotTime.isAfter(lunchStart)) && slotTime.isBefore(lunchEnd)) {
                    continue;
                }
            }
            
            boolean available = !bookedTimes.contains(slotTime);
            
            // Se for hoje, impede agendamento em horário retroativo
            if (available && date.equals(today)) {
                if (slotTime.isBefore(nowTime)) {
                    available = false;
                }
            }
            
            slots.add(new AvailabilitySlot(
                slotTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                available
            ));
        }
        
        return slots;
    }

    private boolean isStaffAbsent(UUID staffId, LocalDateTime bookingDate) {
        try {
            List<br.com.easypet.booking.client.dto.StaffAbsenceResponseDto> absences = partnerServiceClient.getStaffAbsences(staffId);
            if (absences == null) return false;
            return absences.stream().anyMatch(absence -> 
                !bookingDate.isBefore(absence.startDate()) && !bookingDate.isAfter(absence.endDate())
            );
        } catch (Exception e) {
            log.error("Erro ao verificar ausência do profissional: {}", e.getMessage());
            return false;
        }
    }

    private enum ScheduleStatus { AVAILABLE, LUNCH_BREAK, OUT_OF_SCHEDULE }

    private ScheduleStatus getStaffScheduleStatus(UUID staffId, LocalDateTime bookingDate) {
        try {
            List<br.com.easypet.booking.client.dto.StaffScheduleResponseDto> schedules = partnerServiceClient.getStaffSchedule(staffId);
            if (schedules == null || schedules.isEmpty()) return ScheduleStatus.OUT_OF_SCHEDULE;

            // Mapeia DayOfWeek Java (Seg=1…Dom=7) → padrão partner-service (Dom=1, Seg=2…)
            int targetDay = bookingDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? 1 : bookingDate.getDayOfWeek().getValue() + 1;
            LocalTime targetTime = bookingDate.toLocalTime();

            for (var s : schedules) {
                if (s.dayOfWeek() != targetDay) continue;
                boolean withinWork = !targetTime.isBefore(s.startTime()) && targetTime.isBefore(s.endTime());
                if (!withinWork) continue;
                // Dentro do expediente — verificar intervalo de almoço
                if (s.lunchStartTime() != null && s.lunchEndTime() != null &&
                    !targetTime.isBefore(s.lunchStartTime()) && targetTime.isBefore(s.lunchEndTime())) {
                    return ScheduleStatus.LUNCH_BREAK;
                }
                return ScheduleStatus.AVAILABLE;
            }
            return ScheduleStatus.OUT_OF_SCHEDULE;
        } catch (Exception e) {
            log.error("Erro ao verificar escala do profissional {}: {}", staffId, e.getMessage());
            return ScheduleStatus.OUT_OF_SCHEDULE;
        }
    }

    private boolean isWithinStaffSchedule(UUID staffId, LocalDateTime bookingDate) {
        return getStaffScheduleStatus(staffId, bookingDate) == ScheduleStatus.AVAILABLE;
    }

    private boolean hasStaffConflict(UUID partnerId, UUID staffId, LocalDateTime bookingDate) {
        try {
            LocalDateTime startOfDay = bookingDate.toLocalDate().atStartOfDay();
            LocalDateTime endOfDay = bookingDate.toLocalDate().atTime(23, 59, 59);
            List<Booking> staffBookings = bookingRepository.findByPartnerIdAndStaffIdAndStatusNotAndBookingDateBetween(
                    partnerId, staffId, BookingStatus.CANCELLED, startOfDay, endOfDay
            );
            if (staffBookings == null) return false;
            return staffBookings.stream().anyMatch(b -> 
                b.getBookingDate().equals(bookingDate) && (b.getIsFittingRequest() == null || !b.getIsFittingRequest())
            );
        } catch (Exception e) {
            log.error("Erro ao verificar conflito de horário do profissional: {}", e.getMessage());
            return false;
        }
    }

    public FittingDecisionResponse processFittingDecision(UUID bookingId, FittingDecisionRequest request) {
        log.info("Processando decisão de encaixe '{}' para o agendamento ID: {}", request.decision(), bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado com o ID: " + bookingId));

        if (booking.getIsFittingRequest() == null || !booking.getIsFittingRequest()) {
            throw new BusinessException("Este agendamento não é uma solicitação de encaixe.");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("Este agendamento já foi processado (Status: " + booking.getStatus() + ").");
        }

        if ("APPROVE".equalsIgnoreCase(request.decision())) {
            booking.setStatus(BookingStatus.CONFIRMED);
        } else {
            booking.setStatus(BookingStatus.CANCELLED);
        }

        Booking savedBooking = bookingRepository.save(booking);
        log.info("Decisão de encaixe processada com sucesso! Novo status: {}", savedBooking.getStatus());

        return new FittingDecisionResponse(
                savedBooking.getId(),
                savedBooking.getStatus(),
                savedBooking.getIsFittingRequest(),
                savedBooking.getStaffId(),
                savedBooking.getUpdatedAt()
        );
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RevenueStatsResponse getRevenueStats(UUID partnerId, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end   = month.plusMonths(1).atDay(1).atStartOfDay();
        BigDecimal revenue  = bookingRepository.sumRevenueByPartnerAndPeriod(partnerId, start, end);
        return new RevenueStatsResponse(revenue != null ? revenue : BigDecimal.ZERO, month.getMonthValue(), month.getYear());
    }

    @Transactional(readOnly = true)
    public List<DailyStatsEntry> getDailyStats(UUID partnerId, int days) {
        LocalDateTime startDate = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<Object[]> rows = bookingRepository.countDailyBookings(partnerId, startDate);

        // Index results by date string so we can fill gaps with 0
        Map<String, Long> countByDay = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String day   = row[0].toString().substring(0, 10); // "YYYY-MM-DD"
            long   total = ((Number) row[1]).longValue();
            countByDay.put(day, total);
        }

        // Build ordered list covering every day in the range
        List<DailyStatsEntry> result = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            String key = LocalDate.now().minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            result.add(new DailyStatsEntry(key, countByDay.getOrDefault(key, 0L)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ClientStatsResponse getClientStats(UUID partnerId, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end   = month.plusMonths(1).atDay(1).atStartOfDay();
        Long newClients     = bookingRepository.countNewClientsByPartnerAndPeriod(partnerId, start, end);
        return new ClientStatsResponse(newClients != null ? newClients : 0L, month.getMonthValue(), month.getYear());
    }
}
