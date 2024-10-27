package com.github.mhewedy.convo;

import org.springframework.data.annotation.Id;

import java.time.Instant;

/**
 * Base class for all conversation holder.
 */
public abstract class AbstractConversationHolder {
    @Id
    public String id;
    public Object ownerId;
    public String version;
    public Instant expiresAt;
}
