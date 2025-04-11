package com.github.mhewedy.convo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mhewedy.convo.annotations.Step;
import com.github.mhewedy.convo.annotations.Version;
import com.github.mhewedy.convo.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationRepositoryMockTest {

    @Mock
    private IdGenerator idGenerator;
    @Mock
    private StoreRepository storeRepository;

    private ObjectMapper objectMapper;
    private ConversationRepository conversationRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        conversationRepository = new ConversationRepository(idGenerator, objectMapper, storeRepository);
    }

    @Version("1.0")
    static class TestConversation extends AbstractConversationHolder {
        @Step(1)
        public String data;
    }

    @Test
    void update_ShouldSetIdAndVersion_WhenNewObject() {
        // Arrange
        TestConversation conversation = new TestConversation();
        conversation.data = "test";
        String generatedId = "generated-id";
        when(idGenerator.generateNewConversationId()).thenReturn(generatedId);

        // Act
        conversationRepository.update("owner1", conversation);

        // Assert
        assertEquals(generatedId, conversation.id);
        assertEquals("1.0", conversation._version);
        assertEquals("owner1", conversation._ownerId);
        verify(storeRepository).update(conversation);
    }

    @Test
    void update_ShouldThrowException_WhenObjectIsNull() {
        assertThrows(ConversationException.class, () ->
                conversationRepository.update("owner1", null)
        );
    }

    @Test
    void findById_ShouldReturnConversation_WhenValidIdAndOwner() {
        // Arrange
        TestConversation conversation = new TestConversation();
        conversation._ownerId = "owner1";
        conversation._version = "1.0";
        when(storeRepository.findById("test-id", TestConversation.class))
                .thenReturn(Optional.of(conversation));

        // Act
        TestConversation result = conversationRepository.findById("owner1", "test-id", TestConversation.class);

        // Assert
        assertNotNull(result);
        assertEquals("owner1", result._ownerId);
    }

    @Test
    void findById_ShouldThrowException_WhenInvalidOwner() {
        // Arrange
        TestConversation conversation = new TestConversation();
        conversation._ownerId = "owner1";
        when(storeRepository.findById("test-id", TestConversation.class))
                .thenReturn(Optional.of(conversation));

        // Act & Assert
        assertThrows(ConversationException.class, () ->
                conversationRepository.findById("owner2", "test-id", TestConversation.class)
        );
    }

    @Test
    void remove_ShouldRemoveConversation_WhenValidIdAndOwner() {
        // Arrange
        TestConversation conversation = new TestConversation();
        conversation._ownerId = "owner1";
        when(storeRepository.findById("test-id", TestConversation.class))
                .thenReturn(Optional.of(conversation));

        // Act
        conversationRepository.remove("owner1", "test-id", TestConversation.class);

        // Assert
        verify(storeRepository).remove(conversation);
    }

    @Test
    void remove_ShouldNotRemoveConversation_WhenInvalidOwner() {
        // Arrange
        TestConversation conversation = new TestConversation();
        conversation._ownerId = "owner1";
        when(storeRepository.findById("test-id", TestConversation.class))
                .thenReturn(Optional.of(conversation));

        // Act & Assert
        assertThrows(ConversationException.class, () ->
                conversationRepository.remove("owner2", "test-id", TestConversation.class)
        );
        verify(storeRepository, never()).remove(any());
    }

    @Test
    void findById_ShouldThrowException_WhenVersionMismatch() {
        // Arrange
        TestConversation conversation = new TestConversation();
        conversation._ownerId = "owner1";
        conversation._version = "0.9"; // Different from the @Version annotation
        when(storeRepository.findById("test-id", TestConversation.class))
                .thenReturn(Optional.of(conversation));

        // Act & Assert
        assertThrows(ConversationException.class, () ->
                conversationRepository.findById("owner1", "test-id", TestConversation.class)
        );
    }
}
