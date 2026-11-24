package com.connecta.backend.meta.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Shared read step of the profile adapters (Template Method): runs the Graph GET, reads the name and turns any
 * failure into an empty answer. Only the status is logged, never the token or the customer.
 */
@Slf4j
abstract class GraphCustomerProfileClient implements CustomerProfileClient {

    protected Optional<GraphProfile> get(RestClient.RequestHeadersSpec<?> request) {
        try {
            return Optional.ofNullable(request.accept(MediaType.APPLICATION_JSON).retrieve().body(GraphProfile.class));
        } catch (RestClientResponseException e) {
            log.info("Meta profile lookup rejected on {}: status {}", channel(), e.getStatusCode().value());
        } catch (RestClientException e) {
            log.info("Meta profile lookup failed on {}: {}", channel(), e.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GraphProfile(String name, String username) {
    }
}
