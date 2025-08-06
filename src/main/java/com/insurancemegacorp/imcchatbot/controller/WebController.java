package com.insurancemegacorp.imcchatbot.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
    
    @GetMapping("/chat")
    public String chat() {
        return "forward:/index.html";
    }
}