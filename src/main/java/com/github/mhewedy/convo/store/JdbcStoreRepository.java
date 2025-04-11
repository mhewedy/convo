package com.github.mhewedy.convo.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mhewedy.convo.AbstractConversationHolder;
import com.github.mhewedy.convo.ConversationException;
import com.github.mhewedy.convo.config.ConvoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
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
    public <T extends AbstractConversationHolder> void update(T t) {
        t._expiresAt = Instant.now().plus(Util.getTimeToLive(t));
        if (!exists(t)) {
            createNewConversation(t);
        } else {
            updateExistingConversation(t);
        }
    }

    @Override
    @Transactional
    public <T extends AbstractConversationHolder> Optional<T> findById(String id, Class<T> clazz) {
        if (log.isTraceEnabled()) {
            log.trace("find conversation with id: {}, class: {}", id, clazz.getSimpleName());
        }
        try {
            T value = jdbcTemplate.queryForObject("""
                            SELECT id, expires_at, conversation_class, conversation_value
                            FROM conversation_holder
                            WHERE id = :id AND conversation_class = :conversation_class
                            """,
                    new MapSqlParameterSource(Map.of(
                            "id", id,
                            "conversation_class", clazz.getSimpleName()
                    )),
                    (rs, rowNum) -> {
                        try {
                            return objectMapper.readValue(rs.getString("conversation_value"), clazz);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
            if (Instant.now().isAfter(value._expiresAt)) {
                if (log.isDebugEnabled()) {
                    log.debug("conversation with id: {}, conversation class: {} has expired!", id, clazz.getSimpleName());
                }
                remove(value);
                return Optional.empty();
            } else {
                return Optional.of(value);
            }
        } catch (EmptyResultDataAccessException ex) {
            if (log.isDebugEnabled()) {
                log.debug("cannot find conversation object with id: {}, class: {}, reason: {}", id, clazz, ex.getMessage());
            }
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public <T extends AbstractConversationHolder> void remove(T t) {
        if (log.isTraceEnabled()) {
            log.trace("deleting conversation with id: {}, class: {}", t.id, t.getClass().getSimpleName());
        }
        jdbcTemplate.update("""
                        DELETE FROM conversation_holder
                        WHERE id = :id and conversation_class = :conversation_class
                        """,
                new MapSqlParameterSource(Map.of(
                        "id", t.id,
                        "conversation_class", t.getClass().getSimpleName()
                ))
        );
    }

    @PostConstruct
    void startCleanupTask() {
        if (properties.getJdbc().getCleanup().getEnabled()) {
            cleanupExecutorService.scheduleAtFixedRate(() -> transactionTemplate.execute(status -> {
                try {
                    int n = jdbcTemplate.update("DELETE from conversation_holder WHERE expires_at < :now",
                            new MapSqlParameterSource(Map.of("now", Timestamp.from(Instant.now())))
                    );
                    if (log.isTraceEnabled()) {
                        log.trace("deleting expired conversations, {} rows deleted", n);
                    }
                } catch (Exception ex) {
                    log.warn(ex.getMessage());
                }
                return null;
            }), 0, properties.getJdbc().getCleanup().getInterval().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private <T extends AbstractConversationHolder> boolean exists(T t) {
        var sql = "SELECT COUNT(*) FROM conversation_holder WHERE id = :id AND conversation_class = :conversation_class";
        var count = jdbcTemplate.queryForObject(sql,
                new MapSqlParameterSource(Map.of(
                        "id", t.id,
                        "conversation_class", t.getClass().getSimpleName()
                )),
                Integer.class
        );
        return count != null && count > 0;
    }

    private <T extends AbstractConversationHolder> void createNewConversation(T t) {
        if (log.isTraceEnabled()) {
            log.trace("conversation for class: {} already not exists, creating...", t.getClass().getName());
        }

        var map = new HashMap<String, Object>();
        map.put("id", t.id);
        map.put("expires_at", Timestamp.from(t._expiresAt));
        map.put("conversation_class", t.getClass().getSimpleName());
        map.put("conversation_value", toJson(t));
        int update = jdbcTemplate.update("""
                        INSERT INTO conversation_holder (id, expires_at, conversation_class, conversation_value)
                        VALUES (:id, :expires_at, :conversation_class, :conversation_value)
                        """,
                new MapSqlParameterSource(map)
        );
        if (update != 1) {
            throw new ConversationException("failed to insert object: " + t);
        }
    }

    private <T extends AbstractConversationHolder> void updateExistingConversation(T t) {
        if (log.isTraceEnabled()) {
            log.trace("conversation for class: {} already exists, updating...", t.getClass().getName());
        }

        int update = jdbcTemplate.update("""
                        UPDATE conversation_holder
                        SET conversation_value = :conversation_value, expires_at = :expires_at
                        WHERE id = :id and conversation_class = :conversation_class
                        """,
                new MapSqlParameterSource(Map.of(
                        "id", t.id,
                        "conversation_class", t.getClass().getSimpleName(),
                        "conversation_value", toJson(t),
                        "expires_at", Timestamp.from(t._expiresAt)
                ))
        );
        if (update != 1) {
            throw new ConversationException("failed to update object: " + t);
        }
    }

    private <T extends AbstractConversationHolder> String toJson(T t) throws RuntimeException {
        try {
            return objectMapper.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
