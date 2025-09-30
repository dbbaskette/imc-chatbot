package com.insurancemegacorp.imcchatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Reports core LLM configuration on startup so local setups can double check token limits.
 */
@Component
public class LlmStatusReporter {

    private static final Logger log = LoggerFactory.getLogger(LlmStatusReporter.class);

    private final Environment environment;
    private final String model;
    private final String baseUrl;
    private final double temperature;
    private final int maxCompletionTokens;
    private final int contextWindow;
    private final String systemPrompt;

    public LlmStatusReporter(
            Environment environment,
            @Value("${spring.ai.openai.chat.options.model:UNSPECIFIED}") String model,
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.temperature:1.0}") double temperature,
            @Value("${spring.ai.openai.chat.options.max_completion_tokens:-1}") int maxCompletionTokens,
            @Value("${imc.chatbot.llm.context-window:-1}") int contextWindow,
            @Value("${imc.chatbot.system-prompt:}") String systemPrompt) {
        this.environment = environment;
        this.model = model;
        this.baseUrl = baseUrl;
        this.temperature = temperature;
        this.maxCompletionTokens = maxCompletionTokens;
        this.contextWindow = contextWindow;
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reportLlmStatus() {
        log.info("🤖 ========== LLM STATUS REPORT ==========");
        log.info("🧠 Model: {}", emptyToUnknown(model));
        log.info("🌐 Base URL: {}", emptyToUnknown(baseUrl));
        log.info("🎛 Temperature: {}", formatTemperature(temperature));
        log.info("🔢 Max Completion Tokens: {}", formatCount(maxCompletionTokens));
        log.info("🧮 Context Window: {}", formatCount(contextWindow));

        if (contextWindow > 0 && maxCompletionTokens > 0) {
            int promptBudget = contextWindow - maxCompletionTokens;
            if (promptBudget < 0) {
                log.warn("⚠️  Completion token cap exceeds context window by {} tokens", -promptBudget);
            } else {
                log.info("🧾 Prompt Budget (context - completion): {} tokens", promptBudget);
            }
        }

        long promptLines = systemPrompt.isEmpty() ? 0 : systemPrompt.lines().count();
        log.info("📏 System Prompt: {} chars across {} lines", systemPrompt.length(), promptLines);

        String[] profiles = environment.getActiveProfiles();
        log.info("🪧 Active Profiles: {}", profiles.length > 0 ? String.join(", ", profiles) : "default");
        log.info("🤖 ======================================");
    }

    private String emptyToUnknown(String value) {
        return (value == null || value.trim().isEmpty()) ? "UNKNOWN" : value;
    }

    private String formatCount(int value) {
        return value > -1 ? String.valueOf(value) : "UNKNOWN";
    }

    private String formatTemperature(double value) {
        return String.format("%.2f", value);
    }
}
