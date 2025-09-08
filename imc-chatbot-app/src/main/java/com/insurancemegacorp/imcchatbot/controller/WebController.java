package com.insurancemegacorp.imcchatbot.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    /**
     * Serve the main chat interface
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }
    
    /**
     * Health check endpoint for the web interface
     */
    @GetMapping("/health")
    public String health() {
        return "redirect:/api/chat/health";
    }
}