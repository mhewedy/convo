package com.github.mhewedy.convo;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class ConversationFilter extends OncePerRequestFilter {


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String conversationId = request.getHeader(Constants.X_CONVERSATION_ID);
        if (conversationId != null) {
            request.setAttribute(Constants.CONVERSATION_FIELD, conversationId);
        }
        filterChain.doFilter(request, response);
    }
}
