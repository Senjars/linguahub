package io.github.senjar.userservice.service;

import io.github.senjar.userservice.exception.KeycloakAdminException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final RestClient keycloakRestClient;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin-client-id}")
    private String adminClientId;

    @Value("${keycloak.admin-client-secret}")
    private String adminClientSecret;

    public String createUser(String username, String email, String password) {
        String token = getAdminToken();

        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", false
                ))
        );

        ResponseEntity<Void> response = keycloakRestClient.post()
                .uri("/admin/realms/{realm}/users", realm)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        if (location == null) {
            throw new KeycloakAdminException("Keycloak did not return a Location header for the created user");
        }
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String getAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", adminClientId);
        form.add("client_secret", adminClientSecret);

        Map<?, ?> response = keycloakRestClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new KeycloakAdminException("Failed to obtain admin token from Keycloak");
        }
        return (String) response.get("access_token");
    }
}
