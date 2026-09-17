package io.github.senjar.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "booking-service")
public interface BookingClient {

    @PatchMapping("/api/v1/bookings/{bookingId}/confirm")
    void confirmBooking(@PathVariable("bookingId") Long bookingId);
}
