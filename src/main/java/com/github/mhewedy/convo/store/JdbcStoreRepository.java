package com.github.mhewedy.convo.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mhewedy.convo.AbstractConversationHolder;
import com.github.mhewedy.convo.ConversationException;
import com.github.mhewedy.convo.config.ConvoProperties;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This implementation requires a table to hold conversation data.
 * The table should have the following structure:
 *
 * <ul>
 *     <li>{@code id}: A unique identifier for the conversation (e.g., VARCHAR(50))</li>
 *     <li>{@code expires_at}: A timestamp indicating when the conversation expires (e.g., DATETIME, TIMESTAMP)</li>
 *     <li>{@code conversation_class}: A string indicating the class or type of the conversation (e.g., VARCHAR(500))</li>
 *     <li>{@code conversation_value}: The data of the conversation, stored as large text or serialized data (e.g., VARCHAR(8000), TEXT)</li>
 * </ul>
 *
 * <p>
 * SQL definitions for creating the {@code conversation_holder} table are provided in separate files located in the
 * {@code src/resources/sql} directory. These files contain the SQL required to create the table and recommended indexes
 * for various databases.
 * </p>
 * <p>
 * Ensure to use the appropriate SQL file based on your environment for optimal compatibility and performance.
 * </p>
 */
@Slf4j
public class JdbcStoreRepository implements StoreRepository {

    private static final String SQL_SELECT = "SELECT conversation_value FROM conversation_holder WHERE id = :id AND conversation_class = :conversation_class";
    private static final String SQL_INSERT = "INSERT INTO conversation_holder (id, expires_at, conversation_class, conversation_value) VALUES (:id, :expires_at, :conversation_class, :conversation_value)";
    private static final String SQL_UPDATE = "UPDATE conversation_holder SET conversation_value = :conversation_value, expires_at = :expires_at WHERE id = :id and conversation_class = :conversation_class";
    private static final String SQL_DELETE = "DELETE FROM conversation_holder WHERE id = :id and conversation_class = :conversation_class";
    private static final String SQL_CLEANUP = "DELETE from conversation_holder  WHERE expires_at < :now";

    private final ObjectMapper objectMapper;
    private final ConvoProperties properties;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ScheduledExecutorService cleanupExecutorService;
    private final TransactionTemplate transactionTemplate;

    public JdbcStoreRepository(ObjectMapper objectMapper, NamedParameterJdbcTemplate jdbcTemplate,
                               ConvoProperties properties, ScheduledExecutorService cleanupExecutorService,
                               TransactionTemplate transactionTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.cleanupExecutorService = cleanupExecutorService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    @Transactional
    public <T extends AbstractConversationHolder> void save(T t) {
        var exists = jdbcTemplate.query(SQL_SELECT, createParams(t.id, t.getClass()), ResultSet::next);
        t._expiresAt = Instant.now().plus(Util.getTimeToLive(t));

        if (Boolean.TRUE.equals(exists)) {
            updateExistingConversation(t);
        } else {
            createNewConversation(t);
        }
    }

    @Override
    @Transactional
    public <T extends AbstractConversationHolder> Optional<T> findById(String id, Class<T> clazz) {
        log.trace("find conversation with id: {}, class: {}", id, clazz.getSimpleName());
        try {
            T value = jdbcTemplate.queryForObject(SQL_SELECT, createParams(id, clazz),
                    (rs, rowNum) -> fromJson(rs.getString("conversation_value"), clazz));

            if (value == null || Instant.now().isAfter(value._expiresAt)) {
                if (value == null) {
                    log.debug("conversation not found: {}", id);
                } else {
                    remove(value);
                    log.debug("conversation expired: {}", id);
                }
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (EmptyResultDataAccessException ex) {
            log.debug("conversation not found: {}, reason: {}", id, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public <T extends AbstractConversationHolder> void remove(T t) {
        log.trace("deleting conversation with id: {}, class: {}", t.id, t.getClass().getSimpleName());
        jdbcTemplate.update(SQL_DELETE, createParams(t.id, t.getClass()));
    }

    @PostConstruct
    void startCleanupTask() {
        if (properties.getJdbc().getCleanup().getEnabled()) {
            cleanupExecutorService.scheduleAtFixedRate(() -> transactionTemplate.execute(status -> {
                try {
                    int n = jdbcTemplate.update(SQL_CLEANUP, new MapSqlParameterSource(Map.of("now", Timestamp.from(Instant.now()))));
                    log.trace("deleting expired conversations, {} rows deleted", n);
                } catch (Exception ex) {
                    log.warn(ex.getMessage());
                }
                return null;
            }), 0, properties.getJdbc().getCleanup().getInterval().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private <T extends AbstractConversationHolder> void createNewConversation(T t) {
        log.trace("conversation for class: {} does not exist, creating...", t.getClass().getName());

        var params = createParams(t.id, t.getClass());
        params.addValue("expires_at", Timestamp.from(t._expiresAt));
        params.addValue("conversation_value", toJson(t));

        int update = jdbcTemplate.update(SQL_INSERT, params);
        if (update != 1) {
            throw new ConversationException("failed to insert object", "object", t);
        }
    }

    private <T extends AbstractConversationHolder> void updateExistingConversation(T t) {
        log.trace("conversation for class: {} already exists, updating...", t.getClass().getName());

        var params = createParams(t.id, t.getClass());
        params.addValue("expires_at", Timestamp.from(t._expiresAt));
        params.addValue("conversation_value", toJson(t));

        int update = jdbcTemplate.update(SQL_UPDATE, params);
        if (update != 1) {
            throw new ConversationException("failed to update object", "object", t);
        }
    }

    private <T extends AbstractConversationHolder> String toJson(T t) throws RuntimeException {
        try {
            return objectMapper.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows({JsonProcessingException.class})
    private <T extends AbstractConversationHolder> T fromJson(String str, Class<T> clazz) {
        return objectMapper.readValue(str, clazz);
    }

    private <T extends AbstractConversationHolder> MapSqlParameterSource createParams(String id, Class<T> clazz) {
        return new MapSqlParameterSource(Map.of("id", id, "conversation_class", clazz.getSimpleName()));
    }
}
