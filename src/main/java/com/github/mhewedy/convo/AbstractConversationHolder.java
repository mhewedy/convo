package com.github.mhewedy.convo;

import java.time.Instant;

/**
 * Base class for all conversation holder.
 */
public abstract class AbstractConversationHolder {
    public String id;
    public Object ownerId;
    public String version;
    public Instant expiresAt;
}
