package com.github.mhewedy.convo;

import lombok.RequiredArgsConstructor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.github.mhewedy.convo.Constants.X_CONVERSATION_ID;

@RequiredArgsConstructor
public class ConversationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String conversationId = request.getHeader(X_CONVERSATION_ID);
        if (conversationId != null) {
            request.setAttribute(X_CONVERSATION_ID, conversationId);
        }
        filterChain.doFilter(request, response);
    }

    public static String getCurrentConversationId() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return (String) attrs.getAttribute(X_CONVERSATION_ID, RequestAttributes.SCOPE_REQUEST);
    }

    static void setCurrentConversationId(String conversationId) {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        attrs.setAttribute(X_CONVERSATION_ID, conversationId, RequestAttributes.SCOPE_REQUEST);
    }
}
