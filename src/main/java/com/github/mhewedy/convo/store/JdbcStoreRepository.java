package com.github.mhewedy.convo.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mhewedy.convo.AbstractConversationHolder;
import com.github.mhewedy.convo.ConversationException;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * <pre>
 * create table conversation_holder
 * (
 *     id                 varchar(50) primary key,
 *     owner_id           varchar(10),
 *     version            varchar(10),
 *     expires_at         datetime,
 *     conversation_class varchar(500),
 *     conversation_value varchar(8000)
 * );
 * </pre>
 */
@Log4j2
public class JdbcStoreRepository implements StoreRepository {

    private final ObjectMapper objectMapper;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcStoreRepository(ObjectMapper objectMapper, NamedParameterJdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public <T extends AbstractConversationHolder> void update(T t) {
        if (exists(t)) {
            int update = jdbcTemplate.update("""
                            UPDATE conversation_holder
                            SET conversation_value = :conversation_value
                            WHERE id = :id and conversation_class = :conversation_class
                            """,
                    new MapSqlParameterSource(Map.of(
                            "id", t.id,
                            "conversation_class", t.getClass().getSimpleName(),
                            "conversation_value", toJson(t)
                    ))
            );
            if (update != 1) {
                throw new ConversationException("failed to update object");
            }
        } else {
            t.expiresAt = Instant.now().plus(Util.getTimeToLive(t));
            var map = new HashMap<String, Object>();
            map.put("id", t.id);
            map.put("owner_id", t.ownerId);
            map.put("version", t.version);
            map.put("expires_at", Timestamp.from(t.expiresAt));
            map.put("conversation_class", t.getClass().getSimpleName());
            map.put("conversation_value", toJson(t));
            int update = jdbcTemplate.update("""
                            INSERT INTO conversation_holder (id, owner_id, version, expires_at, conversation_class, conversation_value)
                            VALUES (:id, :owner_id, :version, :expires_at, :conversation_class, :conversation_value)
                            """,
                    new MapSqlParameterSource(map)
            );
            if (update != 1) {
                throw new ConversationException("failed to insert object");
            }
        }
    }

    @Override
    public <T extends AbstractConversationHolder> Optional<T> findById(String id, Class<T> clazz) {
        try {
            T value = jdbcTemplate.queryForObject("""
                            SELECT id, owner_id, version, expires_at, conversation_class, conversation_value
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
            if (Instant.now().isAfter(value.expiresAt)) {
                log.debug("conversation with id: {}, conversation class: {} has expired!", id, clazz.getSimpleName());
                delete(value);
                return Optional.empty();
            } else {
                return Optional.of(value);
            }
        } catch (EmptyResultDataAccessException ex) {
            log.warn(ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public <T extends AbstractConversationHolder> void delete(T t) {
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

    private <T extends AbstractConversationHolder> String toJson(T t) throws RuntimeException {
        try {
            return objectMapper.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
