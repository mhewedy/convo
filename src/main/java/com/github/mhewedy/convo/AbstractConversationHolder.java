package com.github.mhewedy.convo;

import java.time.Instant;

/**
 * Base class for all conversation holder.
 */
public abstract class AbstractConversationHolder {
    public String id;
    public String _ownerId;
    public String _version;
    public Instant _expiresAt;
}
