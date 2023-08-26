package com.github.mhewedy.convo;

import brave.baggage.BaggageField;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationTraceFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String conversationId = request.getHeader(Constants.X_CONVERSATION_ID);
        BaggageField baggageField = BaggageField.getByName(Constants.CONVERSATION_TRACE_FIELD);

        baggageField.updateValue(Objects.requireNonNullElseGet(conversationId, () -> TraceUtil.getId(tracer)));

        filterChain.doFilter(request, response);
    }
}
