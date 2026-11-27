package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Participant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");
    private static final Pattern ERROR_SUBCODE = Pattern.compile("\"error_subcode\"\\s*:\\s*(\\d+)");
    private static final String NOT_GIVEN = "none";

    /** Only the numeric Graph error code, never the message: it can name the customer. */
    private static String errorField(RestClientResponseException e, Pattern field) {
        Matcher match = field.matcher(e.getResponseBodyAsString());
        return match.find() ? match.group(1) : NOT_GIVEN;
    }

    protected Optional<GraphProfile> get(RestClient.RequestHeadersSpec<?> request) {
        try {
            return Optional.ofNullable(request.accept(MediaType.APPLICATION_JSON).retrieve().body(GraphProfile.class));
        } catch (RestClientResponseException e) {
            log.info("Meta profile lookup rejected on {}: status {}, Graph error code {} subcode {}", channel(),
                    e.getStatusCode().value(), errorField(e, ERROR_CODE), errorField(e, ERROR_SUBCODE));
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
