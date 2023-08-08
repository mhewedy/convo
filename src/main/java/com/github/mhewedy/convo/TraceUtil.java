package com.github.mhewedy.convo;


import io.micrometer.tracing.Tracer;

class TraceUtil {
    static String getId(Tracer tracer) {
        return tracer.currentSpan().context().traceId();
    }
}
