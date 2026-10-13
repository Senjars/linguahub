package io.github.senjar.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class KeycloakAdminServiceTest {

    private static final String BASE = "https://keycloak.test";
    private static final String REALM = "linguahub";
    private static final String USER_ID = "user-1";
    private static final String TOKEN_URL =
            BASE + "/realms/" + REALM + "/protocol/openid-connect/token";
    private static final String TOKEN_RESPONSE = "{\"access_token\":\"tok\"}";

    private MockRestServiceServer server;
    private KeycloakAdminService keycloakAdminService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        keycloakAdminService = new KeycloakAdminService(builder.build());
        ReflectionTestUtils
                .setField(keycloakAdminService, "realm", REALM);
        ReflectionTestUtils
                .setField(keycloakAdminService, "adminClientId", "admin-client");
        ReflectionTestUtils
                .setField(keycloakAdminService, "adminClientSecret", "test-secret");
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("Should return the id taken from the Location header")
        void createUser_validRequest_returnsIdFromLocationHeader() {
            // GIVEN
            expectToken();
            server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer tok"))
                    .andRespond(withStatus(HttpStatus.CREATED)
                            .location(URI.create(
                                    BASE + "/admin/realms/" + REALM + "/users/" + USER_ID)));

            // WHEN
            String id = keycloakAdminService.createUser(
                    "jan", "jan@example.com", "password123");

            // THEN
            assertThat(id).isEqualTo(USER_ID);
            server.verify();
        }
    }

    @Nested
    @DisplayName("assignRealmRole")
    class AssignRealmRole {

        @Test
        @DisplayName("Should read the realm role and map it to the user")
        void assignRealmRole_existingRole_mapsRoleToUser() {
            // GIVEN
            expectToken();
            server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/roles/STUDENT"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("Authorization", "Bearer tok"))
                    .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"STUDENT\"}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/users/" + USER_ID
                            + "/role-mappings/realm"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer tok"))
                    .andExpect(jsonPath("$[0].id").value("role-1"))
                    .andExpect(jsonPath("$[0].name").value("STUDENT"))
                    .andRespond(withNoContent());

            // WHEN
            keycloakAdminService.assignRealmRole(USER_ID, "STUDENT");

            // THEN
            server.verify();
        }

        @Test
        @DisplayName("Should fail without mapping anything "
                + "when the role does not exist in the realm")
        void assignRealmRole_unknownRole_throwsAndMapsNothing() {
            // GIVEN
            expectToken();
            server.expect(requestTo(BASE + "/admin/realms/" + REALM + "/roles/TEACHER"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));

            // WHEN / THEN
            assertThatThrownBy(() -> keycloakAdminService
                    .assignRealmRole(USER_ID, "TEACHER"))
                    .isInstanceOf(RestClientResponseException.class);
            server.verify();
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("Should delete the user in the realm")
        void deleteUser_existingUser_sendsDelete() {
            // GIVEN
            expectToken();
            server.expect(requestTo(
                    BASE + "/admin/realms/" + REALM + "/users/" + USER_ID))
                    .andExpect(method(HttpMethod.DELETE))
                    .andExpect(header("Authorization", "Bearer tok"))
                    .andRespond(withNoContent());

            // WHEN
            keycloakAdminService.deleteUser(USER_ID);

            // THEN
            server.verify();
        }
    }

    private void expectToken() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
    }
}
