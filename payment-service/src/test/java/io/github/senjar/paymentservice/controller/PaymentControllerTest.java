package io.github.senjar.paymentservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.senjar.paymentservice.config.SecurityConfig;
import io.github.senjar.paymentservice.dto.PaymentResponseDto;
import io.github.senjar.paymentservice.service.PaymentService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    private static final String BASE_URL = "/api/v1/payments";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long BOOKING_ID = 10L;
    private static final String SESSION_ID = "cs_test_123";
    private static final String PAYLOAD = "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\"}";
    private static final String SIGNATURE = "t=1,v1=abc";
    private static final String CANCEL_MESSAGE = "Payment cancelled successfully.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    // Required by the resource server configuration, tokens are injected by jwt() in the tests
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Nested
    @DisplayName("GET /api/v1/payments")
    class GetMyPayments {

        @Test
        @DisplayName("Should return 200 OK and the page of payments of the user from the token")
        void getMyPayments_withToken_returnsPageOfUser() throws Exception {
            // GIVEN
            PaymentResponseDto response = providePaymentResponse();
            when(paymentService.getPaymentsByUserId(eq(USER_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(response)));

            // WHEN & THEN
            mockMvc.perform(get(BASE_URL).with(provideJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        @DisplayName("Should pass the page and size request parameters to the service")
        void getMyPayments_pagingParameters_arePassedToService() throws Exception {
            // GIVEN
            when(paymentService.getPaymentsByUserId(eq(USER_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            // WHEN
            mockMvc.perform(get(BASE_URL)
                            .with(provideJwt())
                            .param("page", "1")
                            .param("size", "5"))
                    .andExpect(status().isOk());

            // THEN
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(paymentService).getPaymentsByUserId(eq(USER_ID), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
            assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when there is no token")
        void getMyPayments_noToken_returnsUnauthorized() throws Exception {
            // WHEN & THEN
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(paymentService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/single-lesson")
    class CreateLessonPayment {

        @Test
        @DisplayName("Should create the payment for the user from the token and the given booking")
        void createLessonPayment_validRequest_returnsOk() throws Exception {
            // GIVEN
            PaymentResponseDto response = providePaymentResponse();
            when(paymentService.createSingleLessonPayment(USER_ID, BOOKING_ID))
                    .thenReturn(response);

            // WHEN & THEN
            mockMvc.perform(post(BASE_URL + "/single-lesson")
                            .with(provideJwt())
                            .param("bookingId", BOOKING_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
            verify(paymentService).createSingleLessonPayment(USER_ID, BOOKING_ID);
        }

        @Test
        @DisplayName("Should return 400 Bad Request when bookingId is missing")
        void createLessonPayment_missingBookingId_returnsBadRequest() throws Exception {
            // WHEN & THEN
            mockMvc.perform(post(BASE_URL + "/single-lesson").with(provideJwt()))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("Should return 400 Bad Request when bookingId is not a number")
        void createLessonPayment_nonNumericBookingId_returnsBadRequest() throws Exception {
            // WHEN & THEN
            mockMvc.perform(post(BASE_URL + "/single-lesson")
                            .with(provideJwt())
                            .param("bookingId", "abc"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when there is no token")
        void createLessonPayment_noToken_returnsUnauthorized() throws Exception {
            // WHEN & THEN
            mockMvc.perform(post(BASE_URL + "/single-lesson")
                            .param("bookingId", BOOKING_ID.toString()))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(paymentService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/success")
    class FulfillPayment {

        @Test
        @DisplayName("Should fulfil the payment of the session without requiring a token")
        void fulfillPayment_validSession_returnsOkWithoutToken() throws Exception {
            // GIVEN
            PaymentResponseDto response = providePaymentResponse();
            when(paymentService.fulfillPayment(SESSION_ID)).thenReturn(response);

            // WHEN & THEN
            mockMvc.perform(get(BASE_URL + "/success").param("sessionId", SESSION_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
            verify(paymentService).fulfillPayment(SESSION_ID);
        }

        @Test
        @DisplayName("Should return 400 Bad Request when sessionId is missing")
        void fulfillPayment_missingSessionId_returnsBadRequest() throws Exception {
            // WHEN & THEN
            mockMvc.perform(get(BASE_URL + "/success"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(paymentService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/cancel")
    class HandleCancel {

        @Test
        @DisplayName("Should return the cancellation message without requiring a token")
        void handleCancel_withoutToken_returnsMessage() throws Exception {
            // GIVEN
            when(paymentService.handleCancel()).thenReturn(CANCEL_MESSAGE);

            // WHEN & THEN
            mockMvc.perform(get(BASE_URL + "/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(CANCEL_MESSAGE));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/webhook")
    class HandleWebhook {

        @Test
        @DisplayName("Should pass the raw payload and signature to the service without a token")
        void handleWebhook_validRequest_returnsOkWithoutToken() throws Exception {
            // WHEN & THEN
            mockMvc.perform(post(BASE_URL + "/webhook")
                            .header("Stripe-Signature", SIGNATURE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PAYLOAD))
                    .andExpect(status().isOk());
            verify(paymentService).processWebhook(PAYLOAD, SIGNATURE);
        }

        @Test
        @DisplayName("Should return 400 Bad Request when the Stripe-Signature header is missing")
        void handleWebhook_missingSignatureHeader_returnsBadRequest() throws Exception {
            // WHEN & THEN
            mockMvc.perform(post(BASE_URL + "/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PAYLOAD))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(paymentService);
        }
    }

    private RequestPostProcessor provideJwt() {
        return jwt().jwt(token -> token.subject(USER_ID.toString()));
    }

    private PaymentResponseDto providePaymentResponse() {
        return mock(PaymentResponseDto.class);
    }
}
