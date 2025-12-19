package com.github.mhewedy.convo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mhewedy.convo.annotations.Step;
import com.github.mhewedy.convo.annotations.TimeToLive;
import com.github.mhewedy.convo.annotations.Version;
import com.github.mhewedy.convo.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;
import java.util.UUID;

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
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private ConversationRepository conversationRepository;

    @Version("1")
    private static class TestConversation extends AbstractConversationHolder {
        @Step(1)
        public String data;
    }

    @Version("1")
    @TimeToLive(duration = "PT1S") // 1 second TTL for testing expiration
    private static class ShortLivedConversation extends AbstractConversationHolder {
        @Step(1)
        public String data;
    }

    @Version("1")
    private static class MultiStepConversation extends AbstractConversationHolder {
        @Step(1)
        public String step1Data;

        @Step(2)
        public String step2Data;

        @Step(3)
        public String step3Data;
    }

    @Version("2")
    private static class VersionedConversation extends AbstractConversationHolder {
        @Step(1)
        public String data;
    }

    @Version("1")
    @TimeToLive(duration = "PT5S") // 5 seconds TTL
    private static class LongerLivedConversation extends AbstractConversationHolder {
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
        TestConversation conversation = new TestConversation();
        conversation.data = "test data";
        String ownerId = "testUser";

        // when
        conversationRepository.save(ownerId, conversation);

        // then
        TestConversation retrieved = conversationRepository.findById(ownerId, conversation.id, TestConversation.class);
        assertNotNull(retrieved);
        assertEquals("test data", retrieved.data);
        assertNotNull(retrieved.id);
        assertEquals("1", retrieved._version);
    }

    @Test
    void shouldSaveAndRetrieveConversation_With_LongOwnerId() {
        // given
        TestConversation conversation = new TestConversation();
        conversation.data = "test data";
        Long ownerId = 1234L;

        // when
        conversationRepository.save(ownerId, conversation);

        // then
        TestConversation retrieved = conversationRepository.findById(ownerId, conversation.id, TestConversation.class);
        assertNotNull(retrieved);
        assertEquals("test data", retrieved.data);
        assertNotNull(retrieved.id);
        assertEquals("1", retrieved._version);
    }

    @Test
    void shouldDeleteConversation() {
        // given
        TestConversation conversation = new TestConversation();
        conversation.data = "test data";
        String ownerId = "testUser";
        conversationRepository.save(ownerId, conversation);

        // when
        conversationRepository.delete(ownerId, conversation.id, TestConversation.class);

        // then
        assertThrows(ConversationException.class, () ->
                conversationRepository.findById(ownerId, conversation.id, TestConversation.class));
    }

    @Test
    void shouldSaveExistingConversation() {
        // given
        TestConversation conversation = new TestConversation();
        conversation.data = "initial data";
        String ownerId = "testUser";

        // when - first update (create)
        conversationRepository.save(ownerId, conversation);
        String conversationId = conversation.id;

        // and - second update (modify)
        conversation.data = "updated data";
        conversationRepository.save(ownerId, conversation);

        // then
        TestConversation retrieved = conversationRepository.findById(ownerId, conversationId, TestConversation.class);
        assertNotNull(retrieved);
        assertEquals("updated data", retrieved.data);
        assertEquals(conversationId, retrieved.id);
    }

    @Test
    void shouldExpireConversation() throws InterruptedException {
        // given
        ShortLivedConversation conversation = new ShortLivedConversation();
        conversation.data = "ephemeral data";
        String ownerId = "testUser";

        // when
        conversationRepository.save(ownerId, conversation);
        String conversationId = conversation.id;

        // Wait for expiration (slightly more than the TTL)
        Thread.sleep(1500);

        // then
        assertThrows(ConversationException.class, () ->
                conversationRepository.findById(ownerId, conversationId, ShortLivedConversation.class));
    }

    @Test
    void shouldCleanupExpiredConversations() throws InterruptedException {
        // given
        ShortLivedConversation conversation1 = new ShortLivedConversation();
        conversation1.data = "ephemeral data 1";
        ShortLivedConversation conversation2 = new ShortLivedConversation();
        conversation2.data = "ephemeral data 2";
        String ownerId = "testUser";

        // when
        conversationRepository.save(ownerId, conversation1);
        conversationRepository.save(ownerId, conversation2);

        // Count before expiration
        int countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversation_holder WHERE conversation_class = :class",
                Map.of("class", ShortLivedConversation.class.getSimpleName()),
                Integer.class);

        // Wait for expiration
        Thread.sleep(1500);

        // Force a find to trigger cleanup of expired conversations for both conversations
        try {
            conversationRepository.findById(ownerId, conversation1.id, ShortLivedConversation.class);
        } catch (ConversationException ignored) {
            // Expected exception
        }

        try {
            conversationRepository.findById(ownerId, conversation2.id, ShortLivedConversation.class);
        } catch (ConversationException ignored) {
            // Expected exception
        }

        // then
        int countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversation_holder WHERE conversation_class = :class",
                Map.of("class", ShortLivedConversation.class.getSimpleName()),
                Integer.class);

        assertEquals(2, countBefore);
        assertEquals(0, countAfter);
    }

    @Test
    void shouldHandleNullOwnerId() {
        // given
        TestConversation conversation = new TestConversation();
        conversation.data = "test data with null owner";

        // when
        conversationRepository.save(null, conversation);

        // then
        TestConversation retrieved = conversationRepository.findById(null, conversation.id, TestConversation.class);
        assertNotNull(retrieved);
        assertEquals("test data with null owner", retrieved.data);
        assertNull(retrieved._ownerId);
    }

    @Test
    void shouldThrowExceptionForNullConversation() {
        // given
        String ownerId = "testUser";

        // when & then
        assertThrows(ConversationException.class, () -> 
                conversationRepository.save(ownerId, null));
    }

    @Test
    void shouldThrowExceptionForNonExistentConversation() {
        // given
        String ownerId = "testUser";
        String nonExistentId = UUID.randomUUID().toString();

        // when & then
        assertThrows(ConversationException.class, () -> 
                conversationRepository.findById(ownerId, nonExistentId, TestConversation.class));
    }

    @Test
    void shouldNotThrowExceptionWhenRemovingNonExistentConversation() {
        // given
        String ownerId = "testUser";
        String nonExistentId = UUID.randomUUID().toString();

        // when & then - should not throw exception
        conversationRepository.delete(ownerId, nonExistentId, TestConversation.class);
    }

    @Test
    void shouldHandleMultiStepConversation() {
        // given
        MultiStepConversation conversation = new MultiStepConversation();
        conversation.step1Data = "step 1 data";
        String ownerId = "testUser";

        // when - update step 1
        conversationRepository.save(ownerId, conversation);
        String conversationId = conversation.id;

        // update step 2
        MultiStepConversation step2Conversation = conversationRepository.findById(ownerId, conversationId, MultiStepConversation.class);
        step2Conversation.step2Data = "step 2 data";
        conversationRepository.save(ownerId, step2Conversation);

        // update step 3
        MultiStepConversation step3Conversation = conversationRepository.findById(ownerId, conversationId, MultiStepConversation.class);
        step3Conversation.step3Data = "step 3 data";
        conversationRepository.save(ownerId, step3Conversation);

        // then
        MultiStepConversation retrieved = conversationRepository.findById(ownerId, conversationId, MultiStepConversation.class);
        assertNotNull(retrieved);
        assertEquals("step 1 data", retrieved.step1Data);
        assertEquals("step 2 data", retrieved.step2Data);
        assertEquals("step 3 data", retrieved.step3Data);
    }

    @Test
    void shouldHandleDifferentTTLValues() throws InterruptedException {
        // given
        ShortLivedConversation shortLived = new ShortLivedConversation();
        shortLived.data = "short lived data";

        LongerLivedConversation longerLived = new LongerLivedConversation();
        longerLived.data = "longer lived data";

        String ownerId = "testUser";

        // when
        conversationRepository.save(ownerId, shortLived);
        conversationRepository.save(ownerId, longerLived);

        // Wait for short-lived to expire but not longer-lived
        Thread.sleep(1500);

        // then
        // Short-lived should be expired
        assertThrows(ConversationException.class, () -> 
                conversationRepository.findById(ownerId, shortLived.id, ShortLivedConversation.class));

        // Longer-lived should still exist
        LongerLivedConversation retrievedLonger = conversationRepository.findById(ownerId, longerLived.id, LongerLivedConversation.class);
        assertNotNull(retrievedLonger);
        assertEquals("longer lived data", retrievedLonger.data);
    }

    @Test
    void shouldThrowExceptionForVersionMismatch() {
        // given
        // Creates a conversation with version 1
        TestConversation conversation = new TestConversation();
        conversation.data = "test data";
        String ownerId = "testUser";
        conversationRepository.save(ownerId, conversation);

        // Manually change the version in the JSON stored in the database
        jdbcTemplate.update(
                "UPDATE conversation_holder SET conversation_value = REPLACE(conversation_value, '\"_version\":\"1\"', '\"_version\":\"0\"') WHERE id = :id",
                Map.of("id", conversation.id));

        // when & then
        assertThrows(ConversationException.class, () -> 
                conversationRepository.findById(ownerId, conversation.id, TestConversation.class));
    }

    @Test
    void shouldThrowExceptionWhenOwnerIdDoesNotMatch() {
        // given
        TestConversation conversation = new TestConversation();
        conversation.data = "test data";
        String ownerId = "testUser";
        String differentOwnerId = "differentUser";

        // when
        conversationRepository.save(ownerId, conversation);

        // then
        assertThrows(ConversationException.class, () -> 
                conversationRepository.findById(differentOwnerId, conversation.id, TestConversation.class));
    }

    @Test
    void shouldThrowExceptionWhenRemovingWithDifferentOwnerId() {
        // given
        TestConversation conversation = new TestConversation();
        conversation.data = "test data";
        String ownerId = "testUser";
        String differentOwnerId = "differentUser";

        // when
        conversationRepository.save(ownerId, conversation);

        // then
        assertThrows(ConversationException.class, () -> 
                conversationRepository.delete(differentOwnerId, conversation.id, TestConversation.class));
    }

}
