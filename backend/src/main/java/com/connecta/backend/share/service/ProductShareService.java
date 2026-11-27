package com.connecta.backend.share.service;

import com.connecta.backend.file.service.PublicBaseUrlMissingException;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.service.AttachmentTooLargeException;
import com.connecta.backend.inbox.service.AttachmentUpload;
import com.connecta.backend.inbox.service.ConversationDetailService;
import com.connecta.backend.inbox.service.EmptyAttachmentException;
import com.connecta.backend.inbox.service.OutgoingAttachmentService;
import com.connecta.backend.inbox.service.OutgoingMessageService;
import com.connecta.backend.inbox.service.UnsupportedAttachmentTypeException;
import com.connecta.backend.meta.service.AttachmentChannelClientFactory;
import com.connecta.backend.meta.service.ChannelSendFailedException;
import com.connecta.backend.meta.service.UnsupportedAttachmentException;
import com.connecta.backend.product.dto.ProductImageResponse;
import com.connecta.backend.product.service.ProductImageContent;
import com.connecta.backend.product.service.ProductImageNotFoundException;
import com.connecta.backend.product.service.ProductImageService;
import com.connecta.backend.product.service.ProductService;
import com.connecta.backend.share.dto.ProductShareResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sends a product into a conversation: the first image when the channel can deliver one, then the text. A facade
 * over the existing outgoing pipeline, so validation, storage, the Meta call, recording and echo dedupe stay in
 * {@link OutgoingAttachmentService} and {@link OutgoingMessageService}. Not transactional, like those two: the
 * Meta calls hold no database connection. The image is best effort: a refusal or failure to send it is logged
 * and the text still goes. A failure of the text propagates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductShareService {

    private final ConversationDetailService conversations;
    private final ProductService products;
    private final ProductImageService images;
    private final AttachmentChannelClientFactory attachmentChannels;
    private final OutgoingAttachmentService outgoingAttachments;
    private final OutgoingMessageService outgoingMessages;

    public ProductShareResponse share(String subject, UUID organizationId, UUID conversationId, UUID productId,
            String text) {
        Conversation conversation = conversations.find(subject, organizationId, conversationId);
        products.get(subject, organizationId, productId);
        List<MessageResponse> sent = new ArrayList<>();
        if (attachmentChannels.forChannel(conversation.getChannel()).supports(AttachmentKind.IMAGE)) {
            sendFirstImage(subject, organizationId, conversationId, productId).ifPresent(sent::add);
        }
        sent.add(outgoingMessages.send(subject, organizationId, conversationId, text));
        return new ProductShareResponse(sent);
    }

    private Optional<MessageResponse> sendFirstImage(String subject, UUID organizationId, UUID conversationId,
            UUID productId) {
        Optional<ProductImageResponse> first = images.list(subject, organizationId, productId).stream().findFirst();
        if (first.isEmpty()) {
            return Optional.empty();
        }
        try {
            ProductImageContent image = images.content(subject, organizationId, productId, first.get().id());
            return Optional.of(outgoingAttachments.send(subject, organizationId, conversationId,
                    new AttachmentUpload(image.bytes(), image.contentType(), null)));
        } catch (ProductImageNotFoundException | EmptyAttachmentException | AttachmentTooLargeException
                | UnsupportedAttachmentTypeException | UnsupportedAttachmentException
                | PublicBaseUrlMissingException | ChannelSendFailedException notSent) {
            log.warn("The product image was not sent, sending the text only: {}", notSent.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
