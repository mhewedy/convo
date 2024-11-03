package com.github.mhewedy.convo;

import lombok.RequiredArgsConstructor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
            System.err.println("cannot get conversation id, reason: RequestContextHolder.getRequestAttributes() is null");
            return null;
        }
        return (String) attrs.getAttribute(X_CONVERSATION_ID, RequestAttributes.SCOPE_REQUEST);
    }

    static void setCurrentConversationId(String conversationId) {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            System.err.println("cannot set conversation id, reason: RequestContextHolder.getRequestAttributes() is null");
            return;
        }
        attrs.setAttribute(X_CONVERSATION_ID, conversationId, RequestAttributes.SCOPE_REQUEST);
    }
}
