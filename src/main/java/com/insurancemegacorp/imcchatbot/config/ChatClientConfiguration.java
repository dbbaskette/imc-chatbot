package com.insurancemegacorp.imcchatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChatClientConfiguration.class);

    // ChatModel bean is automatically configured by Spring AI OpenAI starter
    // No additional configuration needed for basic setup
}