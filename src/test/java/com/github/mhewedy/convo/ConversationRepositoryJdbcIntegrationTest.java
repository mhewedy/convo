package com.github.mhewedy.convo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mhewedy.convo.annotations.Step;
import com.github.mhewedy.convo.annotations.Version;
import com.github.mhewedy.convo.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"convo.store=jdbc"}
)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(connection = EmbeddedDatabaseConnection.H2)
@Sql(scripts = "/sql/postgres.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ConversationRepositoryJdbcIntegrationTest {

    @Autowired
    private IdGenerator idGenerator;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StoreRepository storeRepository;

    private ConversationRepository conversationRepository;

    @Version("1")
    private static class VersionedTestConversation extends AbstractConversationHolder {
        @Step(1)
        public String data;
    }

    @BeforeEach
    void setUp() {
        conversationRepository = new ConversationRepository(idGenerator, objectMapper, storeRepository);
    }

    @Test
    void shouldSaveAndRetrieveConversation() {
        // given
        VersionedTestConversation conversation = new VersionedTestConversation();
        conversation.data = "test data";
        String ownerId = "testUser";

        // when
        conversationRepository.update(ownerId, conversation);

        // then
        VersionedTestConversation retrieved = conversationRepository.findById(ownerId, conversation.id, VersionedTestConversation.class);
        assertNotNull(retrieved);
        assertEquals("test data", retrieved.data);
        assertNotNull(retrieved.id);
        assertEquals("1", retrieved._version);
    }

    @Test
    void shouldSaveAndRetrieveConversation_With_LongOwnerId() {
        // given
        VersionedTestConversation conversation = new VersionedTestConversation();
        conversation.data = "test data";
        Long ownerId = 1234L;

        // when
        conversationRepository.update(ownerId, conversation);

        // then
        VersionedTestConversation retrieved = conversationRepository.findById(ownerId, conversation.id, VersionedTestConversation.class);
        assertNotNull(retrieved);
        assertEquals("test data", retrieved.data);
        assertNotNull(retrieved.id);
        assertEquals("1", retrieved._version);
    }

    @Test
    void shouldRemoveConversation() {
        // given
        VersionedTestConversation conversation = new VersionedTestConversation();
        conversation.data = "test data";
        String ownerId = "testUser";
        conversationRepository.update(ownerId, conversation);

        // when
        conversationRepository.remove(ownerId, conversation.id, VersionedTestConversation.class);

        // then
        assertThrows(ConversationException.class, () ->
                conversationRepository.findById(ownerId, conversation.id, VersionedTestConversation.class));
    }
}
