package br.com.easypet.booking.mapper;

import br.com.easypet.booking.domain.entity.Booking;
import br.com.easypet.booking.dto.request.BookingRequest;
import br.com.easypet.booking.dto.response.BookingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "isFittingRequest", source = "requestFitting")
    Booking toEntity(BookingRequest request);

    BookingResponse toResponse(Booking booking);

    List<BookingResponse> toResponseList(List<Booking> bookings);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "isFittingRequest", source = "requestFitting")
    void updateEntityFromRequest(BookingRequest request, @MappingTarget Booking booking);
}
