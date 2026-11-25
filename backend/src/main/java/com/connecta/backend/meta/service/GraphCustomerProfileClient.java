package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Participant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Shared read step of the profile adapters (Template Method): runs the Graph GET, reads the name and picture and turns any
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

    /** Keeps only a non-blank name and a usable https picture; empty when neither is left. */
    protected static Optional<CustomerProfile> profileOf(String name, String picture) {
        String cleanName = name == null || name.isBlank() ? null : name;
        String cleanPicture = Participant.isStorablePictureUrl(picture) ? picture : null;
        return cleanName == null && cleanPicture == null ? Optional.empty()
                : Optional.of(new CustomerProfile(cleanName, cleanPicture));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GraphProfile(String name, String username, @JsonProperty("profile_pic") String profilePic) {
    }
}
