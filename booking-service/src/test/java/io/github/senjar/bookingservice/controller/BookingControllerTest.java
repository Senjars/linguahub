package io.github.senjar.bookingservice.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.senjar.bookingservice.config.SecurityConfig;
import io.github.senjar.bookingservice.dto.booking.BookingRequestDto;
import io.github.senjar.bookingservice.dto.booking.BookingResponseDto;
import io.github.senjar.bookingservice.exception.DuplicateBookingException;
import io.github.senjar.bookingservice.exception.EntityNotFoundException;
import io.github.senjar.bookingservice.exception.SlotFullException;
import io.github.senjar.bookingservice.model.booking.Status;
import io.github.senjar.bookingservice.service.BookingService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = BookingController.class)
@Import(SecurityConfig.class)
class BookingControllerTest {

    private static final String BASE_URL = "/api/v1/bookings";
    private static final Long SLOT_ID = 5L;
    private static final Long BOOKING_ID = 10L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "student@example.com";
    private static final String SESSION_URL = "https://checkout.stripe.com/c/pay/test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Nested
    @DisplayName("POST /api/v1/bookings")
    class BookLesson {

        @Test
        @DisplayName("Should create booking for the user from the token "
                + "and return 201 with session url")
        void bookLesson_validRequest_returnsCreated() throws Exception {
            // GIVEN
            BookingRequestDto request = new BookingRequestDto(SLOT_ID);
            BookingResponseDto response = provideBookingResponse(SESSION_URL);
            when(bookingService.createBooking(request, USER_ID, EMAIL)).thenReturn(response);

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .with(provideJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(BOOKING_ID))
                    .andExpect(jsonPath("$.slotId").value(SLOT_ID))
                    .andExpect(jsonPath("$.studentId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.status").value(Status.PENDING.name()))
                    .andExpect(jsonPath("$.sessionUrl").value(SESSION_URL));
            verify(bookingService).createBooking(request, USER_ID, EMAIL);
        }

        @Test
        @DisplayName("Should return 400 Bad Request when slotId is null")
        void bookLesson_invalidRequest_returnsBadRequest() throws Exception {
            // GIVEN
            BookingRequestDto invalidRequest = new BookingRequestDto(null);

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .with(provideJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("Should return 409 Conflict when the slot is full")
        void bookLesson_slotFull_returnsConflict() throws Exception {
            // GIVEN
            BookingRequestDto request = new BookingRequestDto(SLOT_ID);
            when(bookingService.createBooking(request, USER_ID, EMAIL))
                    .thenThrow(new SlotFullException("Slot is full"));

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .with(provideJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should return 409 Conflict with the reason "
                + "when the student already booked the slot")
        void bookLesson_duplicateBooking_returnsConflictWithDetail() throws Exception {
            // GIVEN
            BookingRequestDto request = new BookingRequestDto(SLOT_ID);
            when(bookingService.createBooking(request, USER_ID, EMAIL))
                    .thenThrow(
                            new DuplicateBookingException("Student already has an active booking"));

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .with(provideJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Student already has an active booking"));
        }

        @Test
        @DisplayName("Should return 403 Forbidden when the user is a teacher")
        void bookLesson_teacherRole_returnsForbidden() throws Exception {
            // GIVEN
            BookingRequestDto request = new BookingRequestDto(SLOT_ID);

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .with(provideJwtWithRole("ROLE_TEACHER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when there is no token")
        void bookLesson_noToken_returnsUnauthorized() throws Exception {
            // GIVEN
            BookingRequestDto request = new BookingRequestDto(SLOT_ID);

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(bookingService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bookings/{id}")
    class GetBookingById {

        @Test
        @DisplayName("Should return 200 OK and the booking when it belongs to the student")
        void getBookingById_ownBooking_returnsOk() throws Exception {
            // GIVEN
            when(bookingService.findBooking(BOOKING_ID, USER_ID))
                    .thenReturn(provideBookingResponse(null));

            // WHEN & THEN
            mockMvc.perform(get(BASE_URL + "/{id}", BOOKING_ID).with(provideJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(BOOKING_ID))
                    .andExpect(jsonPath("$.slotId").value(SLOT_ID))
                    .andExpect(jsonPath("$.sessionUrl").doesNotExist());
        }

        @Test
        @DisplayName("Should return 404 Not Found "
                + "when the booking does not exist or belongs to someone else")
        void getBookingById_notOwned_returnsNotFound() throws Exception {
            // GIVEN
            when(bookingService.findBooking(BOOKING_ID, USER_ID))
                    .thenThrow(new EntityNotFoundException("Booking not found"));

            // WHEN & THEN
            mockMvc.perform(get(BASE_URL + "/{id}", BOOKING_ID).with(provideJwt()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when the user is a teacher")
        void getBookingById_teacherRole_returnsForbidden() throws Exception {
            // WHEN & THEN
            mockMvc.perform(get(BASE_URL + "/{id}", BOOKING_ID)
                            .with(provideJwtWithRole("ROLE_TEACHER")))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when there is no token")
        void getBookingById_noToken_returnsUnauthorized() throws Exception {
            // WHEN & THEN
            mockMvc.perform(get(BASE_URL + "/{id}", BOOKING_ID))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(bookingService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bookings")
    class GetAllBookingsForStudent {

        @Test
        @DisplayName("Should return 200 OK and the list of bookings of the student")
        void getAllBookingsForStudent_hasBookings_returnsOkWithList() throws Exception {
            // GIVEN
            List<BookingResponseDto> responses = List.of(provideBookingResponse(null));
            when(bookingService.getBookingsForStudent(USER_ID)).thenReturn(responses);

            // WHEN & THEN
            mockMvc.perform(get(BASE_URL).with(provideJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(BOOKING_ID));
        }

        @Test
        @DisplayName("Should return 200 OK and an empty list when the student has no bookings")
        void getAllBookingsForStudent_noBookings_returnsOkWithEmptyList() throws Exception {
            // GIVEN
            when(bookingService.getBookingsForStudent(USER_ID)).thenReturn(List.of());

            // WHEN & THEN
            mockMvc.perform(get(BASE_URL).with(provideJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Should return 403 Forbidden when the user is a teacher")
        void getAllBookingsForStudent_teacherRole_returnsForbidden() throws Exception {
            // WHEN & THEN
            mockMvc.perform(get(BASE_URL).with(provideJwtWithRole("ROLE_TEACHER")))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when there is no token")
        void getAllBookingsForStudent_noToken_returnsUnauthorized() throws Exception {
            // WHEN & THEN
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(bookingService);
        }
    }

    private RequestPostProcessor provideJwt() {
        return provideJwtWithRole("ROLE_STUDENT");
    }

    private RequestPostProcessor provideJwtWithRole(String role) {
        return jwt()
                .authorities(new SimpleGrantedAuthority(role))
                .jwt(token -> token
                        .subject(USER_ID.toString())
                        .claim("email", EMAIL));
    }

    private BookingResponseDto provideBookingResponse(String sessionUrl) {
        return new BookingResponseDto(BOOKING_ID, SLOT_ID, USER_ID, Status.PENDING, sessionUrl);
    }
}
