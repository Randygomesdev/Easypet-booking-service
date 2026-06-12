package br.com.easypet.booking.controller;

import br.com.easypet.booking.domain.enums.BookingStatus;
import br.com.easypet.booking.dto.request.BookingRequest;
import br.com.easypet.booking.dto.response.BookingResponse;
import br.com.easypet.booking.dto.response.ClientStatsResponse;
import br.com.easypet.booking.dto.response.DailyStatsEntry;
import br.com.easypet.booking.dto.response.RevenueStatsResponse;
import br.com.easypet.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import br.com.easypet.booking.dto.response.AvailabilitySlot;
import br.com.easypet.booking.dto.request.FittingDecisionRequest;
import br.com.easypet.booking.dto.response.FittingDecisionResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Bookings", description = "Endpoints para gerenciamento de agendamentos e reservas do ecossistema EasyPet")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Criar um novo agendamento", description = "Cria um novo agendamento/reserva de serviço. O ID do cliente é extraído automaticamente do token JWT. Retorna o cabeçalho 'Location' com a URI do novo recurso.")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        log.info("Recebida requisição para criar agendamento do tipo {} para o pet {}", request.type(), request.petId());
        BookingResponse response = bookingService.createBooking(request);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar agendamento por ID", description = "Retorna os detalhes completos de um agendamento específico através do seu UUID.")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable("id") UUID id) {
        log.info("Recebida requisição para buscar agendamento ID: {}", id);
        BookingResponse response = bookingService.getBookingById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-bookings")
    @Operation(summary = "Listar agendamentos do usuário autenticado", description = "Retorna uma lista paginada dos agendamentos pertencentes ao cliente autenticado via JWT.")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @org.springdoc.core.annotations.ParameterObject
            @PageableDefault(size = 10, sort = "bookingDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        String currentUserIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getCredentials();
        log.info("Recebida requisição para buscar agendamentos do usuário autenticado: {}, Pageable: {}", currentUserIdStr, pageable);
        Page<BookingResponse> response = bookingService.getBookingsByUserId(UUID.fromString(currentUserIdStr), pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/partner/{partnerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PARTNER')")
    @Operation(summary = "Listar agendamentos de um parceiro", description = "Retorna uma lista paginada de agendamentos. Suporta filtros por data, profissional, status, tipo e período.")
    public ResponseEntity<Page<BookingResponse>> getBookingsByPartner(
            @PathVariable("partnerId") UUID partnerId,
            @RequestParam(value = "date",      required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "staffId",   required = false) UUID staffId,
            @RequestParam(value = "status",    required = false) BookingStatus status,
            @RequestParam(value = "type",      required = false) br.com.easypet.booking.domain.enums.BookingType type,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate",   required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @org.springdoc.core.annotations.ParameterObject
            @PageableDefault(size = 10, sort = "bookingDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.info("Agendamentos parceiro: {}, date: {}, staffId: {}, status: {}, type: {}, start: {}, end: {}", partnerId, date, staffId, status, type, startDate, endDate);
        Page<BookingResponse> response = bookingService.getBookingsByPartnerId(partnerId, date, staffId, status, type, startDate, endDate, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pet/{petId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PARTNER')")
    @Operation(summary = "Listar agendamentos de um pet", description = "Retorna uma lista paginada com o histórico de agendamentos de um pet. Requer role ADMIN ou PARTNER.")
    public ResponseEntity<Page<BookingResponse>> getBookingsByPet(
            @PathVariable("petId") UUID petId,
            @org.springdoc.core.annotations.ParameterObject
            @PageableDefault(size = 10, sort = "bookingDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.info("Recebida requisição para buscar agendamentos do pet: {}, Pageable: {}", petId, pageable);
        Page<BookingResponse> response = bookingService.getBookingsByPetId(petId, pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar um agendamento", description = "Atualiza os dados de data/hora, notas e valores de um agendamento existente.")
    public ResponseEntity<BookingResponse> updateBooking(
            @PathVariable("id") UUID id,
            @Valid @RequestBody BookingRequest request
    ) {
        log.info("Recebida requisição para atualizar agendamento ID: {}", id);
        BookingResponse response = bookingService.updateBooking(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Alterar status do agendamento", description = "Altera apenas o estado/status de uma reserva (ex: CONFIRMED, CANCELLED, COMPLETED).")
    public ResponseEntity<BookingResponse> updateStatus(
            @PathVariable("id") UUID id,
            @RequestParam("status") BookingStatus status
    ) {
        log.info("Recebida requisição para alterar status do agendamento ID: {} para {}", id, status);
        BookingResponse response = bookingService.updateStatus(id, status);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir um agendamento (Soft Delete)", description = "Efetua a exclusão lógica (Soft Delete) de uma reserva. O registro permanece no banco de dados com a data de deleção, mas fica inacessível para listagens e consultas.")
    public ResponseEntity<Void> deleteBooking(@PathVariable("id") UUID id) {
        log.info("Recebida requisição para excluir agendamento ID: {}", id);
        bookingService.deleteBooking(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bookingId}/fitting-decision")
    @PreAuthorize("hasAnyRole('ADMIN', 'PARTNER')")
    @Operation(summary = "Aprovar ou rejeitar uma solicitação de encaixe pendente", description = "Endpoint de moderação para parceiros aprovarem (APPROVE) ou rejeitarem (REJECT) uma solicitação de encaixe pendente.")
    public ResponseEntity<FittingDecisionResponse> processFittingDecision(
            @PathVariable("bookingId") UUID bookingId,
            @Valid @RequestBody FittingDecisionRequest request
    ) {
        log.info("Recebida requisição de moderação de encaixe para o agendamento ID: {}, decisão: {}", bookingId, request.decision());
        FittingDecisionResponse response = bookingService.processFittingDecision(bookingId, request);
        return ResponseEntity.ok(response);
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    @GetMapping("/partner/{partnerId}/stats/revenue")
    @PreAuthorize("hasAnyRole('ADMIN', 'PARTNER')")
    @Operation(summary = "Receita mensal do parceiro", description = "Retorna a soma de preço dos agendamentos com status COMPLETED no mês informado (formato YYYY-MM).")
    public ResponseEntity<RevenueStatsResponse> getRevenueStats(
            @PathVariable("partnerId") UUID partnerId,
            @RequestParam("month") String month
    ) {
        log.info("Stats de receita para parceiro {} no mês {}", partnerId, month);
        return ResponseEntity.ok(bookingService.getRevenueStats(partnerId, YearMonth.parse(month)));
    }

    @GetMapping("/partner/{partnerId}/stats/daily")
    @PreAuthorize("hasAnyRole('ADMIN', 'PARTNER')")
    @Operation(summary = "Agendamentos por dia (últimos N dias)", description = "Retorna a contagem de agendamentos por dia para os últimos N dias (default 7). Preenche com 0 dias sem agendamentos.")
    public ResponseEntity<List<DailyStatsEntry>> getDailyStats(
            @PathVariable("partnerId") UUID partnerId,
            @RequestParam(value = "days", defaultValue = "7") int days
    ) {
        log.info("Stats diários para parceiro {} nos últimos {} dias", partnerId, days);
        return ResponseEntity.ok(bookingService.getDailyStats(partnerId, days));
    }

    @GetMapping("/partner/{partnerId}/stats/clients")
    @PreAuthorize("hasAnyRole('ADMIN', 'PARTNER')")
    @Operation(summary = "Novos clientes no mês", description = "Retorna a contagem de clientes que fizeram o primeiro agendamento com este parceiro no mês informado (formato YYYY-MM).")
    public ResponseEntity<ClientStatsResponse> getClientStats(
            @PathVariable("partnerId") UUID partnerId,
            @RequestParam("month") String month
    ) {
        log.info("Stats de novos clientes para parceiro {} no mês {}", partnerId, month);
        return ResponseEntity.ok(bookingService.getClientStats(partnerId, YearMonth.parse(month)));
    }

    @GetMapping("/availability")
    @Operation(summary = "Obter slots de disponibilidade de um parceiro", description = "Retorna um grid de slots de 30 minutos indicando quais estão livres ou ocupados para agendamento na data especificada.")
    public ResponseEntity<List<AvailabilitySlot>> getAvailability(
            @RequestParam("partnerId") UUID partnerId,
            @RequestParam(value = "serviceId", required = false) UUID serviceId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("Recebida requisição de disponibilidade para o parceiro {}, serviço {} na data {}", partnerId, serviceId, date);
        List<AvailabilitySlot> slots = bookingService.getAvailability(partnerId, serviceId, date);
        return ResponseEntity.ok(slots);
    }
}
