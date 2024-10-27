package com.github.mhewedy.convo;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public interface IdGenerator {

    String generateNewConversationId();

    static String getConversationIdFromRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs == null ? null :
                (String) attrs.getAttribute(Constants.CONVERSATION_FIELD, RequestAttributes.SCOPE_REQUEST);
    }
}
