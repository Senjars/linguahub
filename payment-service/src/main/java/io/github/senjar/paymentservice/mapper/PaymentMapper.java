package io.github.senjar.paymentservice.mapper;

import io.github.senjar.paymentservice.dto.PaymentResponseDto;
import io.github.senjar.paymentservice.model.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(source = "amountToPay", target = "amount")
    PaymentResponseDto toDto(Payment payment);
}
