package com.example.nutritionplanner;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

interface AgentListeners {

    class LoggingListener implements AgentListener {

        private static final Logger log = LoggerFactory.getLogger(LoggingListener.class);

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }

        @Override
        public void beforeAgentInvocation(AgentRequest request) {
            log.info("Agent '{}' invoked with inputs: {}", request.agentName(), request.inputs().keySet());
        }

        @Override
        public void afterAgentInvocation(AgentResponse response) {
            log.info("Agent '{}' completed", response.agentName());
        }
    }

    class MicrometerAgentListener implements AgentListener {

        private final MeterRegistry meterRegistry;
        private final ObservationRegistry observationRegistry;
        private final AtomicInteger activeAgents;

        public MicrometerAgentListener(MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
            this.meterRegistry = meterRegistry;
            this.observationRegistry = observationRegistry;
            this.activeAgents = meterRegistry.gauge("agent.active", new AtomicInteger(0));
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }

        @Override
        public void beforeAgentInvocation(AgentRequest request) {
            activeAgents.incrementAndGet();
            Counter.builder("agent.invocations")
                    .tag("agent", request.agentName())
                    .description("Total agent invocations")
                    .register(meterRegistry)
                    .increment();
            Observation.createNotStarted("agent.duration", observationRegistry)
                    .contextualName("agent " + request.agentName())
                    .lowCardinalityKeyValue("agent", request.agentName())
                    .start()
                    .openScope();
        }

        @Override
        public void afterAgentInvocation(AgentResponse response) {
            finish(null);
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError error) {
            finish(error.error());
        }

        @Override
        public void onAgenticSystemSuspended(AgenticScope agenticScope) {
            finish(null);
        }

        private void finish(@Nullable Throwable error) {
            activeAgents.decrementAndGet();
            var scope = observationRegistry.getCurrentObservationScope();
            if (scope == null) return;

            var observation = scope.getCurrentObservation();
            scope.close();
            observation.lowCardinalityKeyValue("outcome", error == null ? "success" : "error");
            if (error != null) observation.error(error);
            observation.stop();
        }
    }
}
