package com.connecta.backend.inbox.controller;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.dto.ConversationSummaryResponse;
import com.connecta.backend.inbox.service.ConversationFilter;
import com.connecta.backend.inbox.service.ConversationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule (checked by ConversationQueryService through TenantAccessService): any member of the
 * organization, ADMIN or AGENT, may read. A non-member or an unknown organization is 404.
 */
@RestController
@Tag(name = "Conversations", description = "The unified inbox of an organization")
@RequiredArgsConstructor
public class ConversationListController {

    private final ConversationQueryService conversations;

    @Operation(summary = "List the organization's conversations, most recent message first; filter with channel "
            + "and q (customer name) (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "One page of conversation summaries")
    @ApiResponse(responseCode = "400", description = "Page or size out of range, or unknown channel")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Organization not found, or the caller is not a member")
    @GetMapping("/api/organizations/{organizationId}/conversations")
    PageResponse<ConversationSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId,
            @RequestParam(required = false) Channel channel,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        return conversations.list(jwt.getSubject(), organizationId, new ConversationFilter(channel, q), page,
                size);
    }
}
