package com.connecta.backend.share.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.service.AttachmentUpload;
import com.connecta.backend.inbox.service.ConversationDetailService;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.inbox.service.OutgoingAttachmentService;
import com.connecta.backend.inbox.service.OutgoingMessageService;
import com.connecta.backend.meta.service.AttachmentChannelClient;
import com.connecta.backend.meta.service.AttachmentChannelClientFactory;
import com.connecta.backend.meta.service.ChannelSendFailedException;
import com.connecta.backend.meta.service.UnsupportedAttachmentException;
import com.connecta.backend.product.dto.ProductImageResponse;
import com.connecta.backend.product.service.ProductImageContent;
import com.connecta.backend.product.service.ProductImageService;
import com.connecta.backend.product.service.ProductNotFoundException;
import com.connecta.backend.product.service.ProductService;
import com.connecta.backend.share.dto.ProductShareResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductShareServiceTests {

    private static final String SUBJECT = UUID.randomUUID().toString();
    private static final String TEXT = "Desk Lamp";
    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID imageId = UUID.randomUUID();
    private final byte[] bytes = {1, 2, 3};

    @Mock
    ConversationDetailService conversations;
    @Mock
    ProductService products;
    @Mock
    ProductImageService images;
    @Mock
    AttachmentChannelClientFactory attachmentChannels;
    @Mock
    AttachmentChannelClient channel;
    @Mock
    OutgoingAttachmentService outgoingAttachments;
    @Mock
    OutgoingMessageService outgoingMessages;
    @InjectMocks
    ProductShareService service;

    private final MessageResponse imageMessage = mock(MessageResponse.class);
    private final MessageResponse textMessage = mock(MessageResponse.class);

    @BeforeEach
    void conversationOnMessenger() {
        Conversation conversation = mock(Conversation.class);
        lenient().when(conversation.getChannel()).thenReturn(Channel.MESSENGER);
        lenient().when(conversations.find(SUBJECT, orgId, conversationId)).thenReturn(conversation);
        lenient().when(attachmentChannels.forChannel(Channel.MESSENGER)).thenReturn(channel);
    }

    private void productHasAnImage() {
        when(images.list(SUBJECT, orgId, productId))
                .thenReturn(List.of(new ProductImageResponse(imageId, "image/png", 3, 0, "/p", null)));
        when(images.content(SUBJECT, orgId, productId, imageId)).thenReturn(new ProductImageContent("image/png", bytes));
    }

    private ProductShareResponse share() {
        return service.share(SUBJECT, orgId, conversationId, productId, TEXT);
    }

    @Nested
    class Sending {

        @Test
        void sendsTheImageThenTheText() {
            when(channel.supports(AttachmentKind.IMAGE)).thenReturn(true);
            productHasAnImage();
            when(outgoingAttachments.send(any(), any(), any(), any())).thenReturn(imageMessage);
            when(outgoingMessages.send(SUBJECT, orgId, conversationId, TEXT)).thenReturn(textMessage);

            assertEquals(List.of(imageMessage, textMessage), share().messages());
            verify(outgoingAttachments).send(SUBJECT, orgId, conversationId,
                    new AttachmentUpload(bytes, "image/png", null));
        }

        @Test
        void sendsOnlyTheTextWhenTheProductHasNoImage() {
            when(channel.supports(AttachmentKind.IMAGE)).thenReturn(true);
            when(images.list(SUBJECT, orgId, productId)).thenReturn(List.of());
            when(outgoingMessages.send(SUBJECT, orgId, conversationId, TEXT)).thenReturn(textMessage);

            assertEquals(List.of(textMessage), share().messages());
            verifyNoInteractions(outgoingAttachments);
        }

        @Test
        void sendsOnlyTheTextWhenTheChannelCannotDeliverImages() {
            when(channel.supports(AttachmentKind.IMAGE)).thenReturn(false);
            when(outgoingMessages.send(SUBJECT, orgId, conversationId, TEXT)).thenReturn(textMessage);

            assertEquals(List.of(textMessage), share().messages());
            verifyNoInteractions(images, outgoingAttachments);
        }
    }

    @Nested
    class ImageFailure {

        @Test
        void aRefusedImageStillSendsTheText() {
            when(channel.supports(AttachmentKind.IMAGE)).thenReturn(true);
            productHasAnImage();
            when(outgoingAttachments.send(any(), any(), any(), any())).thenThrow(new UnsupportedAttachmentException());
            when(outgoingMessages.send(SUBJECT, orgId, conversationId, TEXT)).thenReturn(textMessage);

            assertEquals(List.of(textMessage), share().messages());
        }

        @Test
        void aFailedImageSendStillSendsTheText() {
            when(channel.supports(AttachmentKind.IMAGE)).thenReturn(true);
            productHasAnImage();
            when(outgoingAttachments.send(any(), any(), any(), any()))
                    .thenThrow(new ChannelSendFailedException("meta"));
            when(outgoingMessages.send(SUBJECT, orgId, conversationId, TEXT)).thenReturn(textMessage);

            assertEquals(List.of(textMessage), share().messages());
        }
    }

    @Nested
    class TextFailure {

        @Test
        void aFailedTextSendPropagates() {
            when(channel.supports(AttachmentKind.IMAGE)).thenReturn(false);
            when(outgoingMessages.send(SUBJECT, orgId, conversationId, TEXT))
                    .thenThrow(new ChannelSendFailedException("meta"));

            assertThrows(ChannelSendFailedException.class, ProductShareServiceTests.this::share);
        }
    }

    @Nested
    class Scoping {

        @Test
        void aForeignProductSendsNothing() {
            when(products.get(SUBJECT, orgId, productId)).thenThrow(new ProductNotFoundException());

            assertThrows(ProductNotFoundException.class, ProductShareServiceTests.this::share);
            verifyNoInteractions(outgoingAttachments, outgoingMessages);
        }

        @Test
        void aForeignConversationSendsNothing() {
            when(conversations.find(SUBJECT, orgId, conversationId)).thenThrow(new ConversationNotFoundException());

            assertThrows(ConversationNotFoundException.class, ProductShareServiceTests.this::share);
            verifyNoInteractions(products, outgoingAttachments, outgoingMessages);
        }
    }
}
