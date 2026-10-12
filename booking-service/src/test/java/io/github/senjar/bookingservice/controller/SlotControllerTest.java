package io.github.senjar.bookingservice.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.senjar.bookingservice.config.SecurityConfig;
import io.github.senjar.bookingservice.dto.slot.CreateSlotDto;
import io.github.senjar.bookingservice.dto.slot.SlotResponseDto;
import io.github.senjar.bookingservice.exception.AccessDeniedException;
import io.github.senjar.bookingservice.exception.EntityNotFoundException;
import io.github.senjar.bookingservice.exception.SlotHasBookingsException;
import io.github.senjar.bookingservice.exception.SlotOverlapException;
import io.github.senjar.bookingservice.service.SlotService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = SlotController.class)
@Import(SecurityConfig.class)
class SlotControllerTest {

    private static final String BASE_URL = "/api/v1/slots";
    private static final Long SLOT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDateTime START = LocalDateTime.now().plusDays(7).withNano(0);
    private static final LocalDateTime END = START.plusHours(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private SlotService slotService;

    // Required by the resource server configuration, tokens are injected by jwt() in the tests
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Nested
    @DisplayName("POST /api/v1/slots")
    class CreateSlot {

        @Test
        @DisplayName("Should create the slot for the teacher from the token and return 201")
        void createSlot_validRequest_returnsCreated() throws Exception {
            // GIVEN
            CreateSlotDto request = provideCreateSlotDto();
            when(slotService.createSlot(USER_ID, request)).thenReturn(provideSlotResponse());

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .with(provideJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(SLOT_ID))
                    .andExpect(jsonPath("$.teacherId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.capacity").value(1))
                    .andExpect(jsonPath("$.bookedCount").value(0));
            verify(slotService).createSlot(USER_ID, request);
        }

        @Test
        @DisplayName("Should return 400 Bad Request when startTime is missing")
        void createSlot_missingStartTime_returnsBadRequest() throws Exception {
            // GIVEN
            CreateSlotDto invalidRequest = new CreateSlotDto(null, END, 1);

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .with(provideJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(slotService);
        }

        @Test
        @DisplayName("Should return 400 Bad Request when endTime is not after startTime")
        void createSlot_endBeforeStart_returnsBadRequest() throws Exception {
            // GIVEN
            CreateSlotDto invalidRequest = new CreateSlotDto(START, START.minusHours(1), 1);

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .with(provideJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(slotService);
        }

        @Test
        @DisplayName("Should return 400 Bad Request when startTime is in the past")
        void createSlot_startInThePast_returnsBadRequest() throws Exception {
            // GIVEN
            CreateSlotDto invalidRequest =
                    new CreateSlotDto(START.minusDays(30), END.minusDays(30), 1);

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .with(provideJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(slotService);
        }

        @Test
        @DisplayName("Should return 400 Bad Request when capacity is not positive")
        void createSlot_nonPositiveCapacity_returnsBadRequest() throws Exception {
            // GIVEN
            CreateSlotDto invalidRequest = new CreateSlotDto(START, END, 0);

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .with(provideJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(slotService);
        }

        @Test
        @DisplayName("Should return 409 Conflict when the teacher already has an overlapping slot")
        void createSlot_overlappingSlot_returnsConflict() throws Exception {
            // GIVEN
            CreateSlotDto request = provideCreateSlotDto();
            when(slotService.createSlot(USER_ID, request))
                    .thenThrow(new SlotOverlapException("You already have a slot overlapping"));

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .with(provideJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when there is no token")
        void createSlot_noToken_returnsUnauthorized() throws Exception {
            // GIVEN
            CreateSlotDto request = provideCreateSlotDto();

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(slotService);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/slots/{slotId}")
    class DeleteSlot {

        @Test
        @DisplayName("Should delete the slot of the teacher from the token and return 204")
        void deleteSlot_ownSlot_returnsNoContent() throws Exception {
            // WHEN & THEN
            mockMvc.perform(delete(BASE_URL + "/{slotId}", SLOT_ID).with(provideJwt()))
                    .andExpect(status().isNoContent());
            verify(slotService).deleteSlot(USER_ID, SLOT_ID);
        }

        @Test
        @DisplayName("Should return 403 Forbidden when the slot belongs to another teacher")
        void deleteSlot_anotherTeachersSlot_returnsForbidden() throws Exception {
            // GIVEN
            doThrow(new AccessDeniedException("You cannot remove slot with id: " + SLOT_ID))
                    .when(slotService).deleteSlot(USER_ID, SLOT_ID);

            // WHEN & THEN
            mockMvc.perform(delete(BASE_URL + "/{slotId}", SLOT_ID).with(provideJwt()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 404 Not Found when the slot does not exist")
        void deleteSlot_nonExistingSlot_returnsNotFound() throws Exception {
            // GIVEN
            doThrow(new EntityNotFoundException("Slot not found with id: " + SLOT_ID))
                    .when(slotService).deleteSlot(USER_ID, SLOT_ID);

            // WHEN & THEN
            mockMvc.perform(delete(BASE_URL + "/{slotId}", SLOT_ID).with(provideJwt()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 409 Conflict when the slot has active bookings")
        void deleteSlot_slotWithBookings_returnsConflict() throws Exception {
            // GIVEN
            doThrow(new SlotHasBookingsException("Cannot delete slot with active bookings"))
                    .when(slotService).deleteSlot(USER_ID, SLOT_ID);

            // WHEN & THEN
            mockMvc.perform(delete(BASE_URL + "/{slotId}", SLOT_ID).with(provideJwt()))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when there is no token")
        void deleteSlot_noToken_returnsUnauthorized() throws Exception {
            // WHEN & THEN
            mockMvc.perform(delete(BASE_URL + "/{slotId}", SLOT_ID))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(slotService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/slots")
    class GetAllSlots {

        @Test
        @DisplayName("Should return 200 OK and the list of slots")
        void getAllSlots_slotsExist_returnsOkWithList() throws Exception {
            // GIVEN
            when(slotService.getAllSlots()).thenReturn(List.of(provideSlotResponse()));

            // WHEN & THEN
            mockMvc.perform(get(BASE_URL).with(provideJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(SLOT_ID));
        }

        @Test
        @DisplayName("Should return 200 OK and an empty list when there are no slots")
        void getAllSlots_noSlots_returnsOkWithEmptyList() throws Exception {
            // GIVEN
            when(slotService.getAllSlots()).thenReturn(List.of());

            // WHEN & THEN
            mockMvc.perform(get(BASE_URL).with(provideJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when there is no token")
        void getAllSlots_noToken_returnsUnauthorized() throws Exception {
            // WHEN & THEN
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(slotService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/slots/{slotId}")
    class GetSlotById {

        @Test
        @DisplayName("Should return 200 OK and the slot")
        void getSlotById_existingSlot_returnsOk() throws Exception {
            // GIVEN
            when(slotService.getSlotById(SLOT_ID)).thenReturn(provideSlotResponse());

            // WHEN & THEN
            mockMvc.perform(get(BASE_URL + "/{slotId}", SLOT_ID).with(provideJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(SLOT_ID))
                    .andExpect(jsonPath("$.teacherId").value(USER_ID.toString()));
        }

        @Test
        @DisplayName("Should return 404 Not Found when the slot does not exist")
        void getSlotById_nonExistingSlot_returnsNotFound() throws Exception {
            // GIVEN
            when(slotService.getSlotById(SLOT_ID))
                    .thenThrow(new EntityNotFoundException("Slot not found with id: " + SLOT_ID));

            // WHEN & THEN
            mockMvc.perform(get(BASE_URL + "/{slotId}", SLOT_ID).with(provideJwt()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when the id is not a number")
        void getSlotById_nonNumericId_returnsBadRequest() throws Exception {
            // WHEN & THEN
            mockMvc.perform(get(BASE_URL + "/{slotId}", "abc").with(provideJwt()))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(slotService);
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when there is no token")
        void getSlotById_noToken_returnsUnauthorized() throws Exception {
            // WHEN & THEN
            mockMvc.perform(get(BASE_URL + "/{slotId}", SLOT_ID))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(slotService);
        }
    }

    private RequestPostProcessor provideJwt() {
        return jwt().jwt(token -> token.subject(USER_ID.toString()));
    }

    private CreateSlotDto provideCreateSlotDto() {
        return new CreateSlotDto(START, END, 1);
    }

    private SlotResponseDto provideSlotResponse() {
        return new SlotResponseDto(SLOT_ID, USER_ID, START, END, 1, 0);
    }
}
