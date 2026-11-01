package ru.ssau.rbs.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;

@RestController 
@RequestMapping("/api/v1/health")
public class CheckHealthController {

    @GetMapping()
    public Map<String, String> getMethodName() {
        return Map.of("status", "ok");
    }
    
}
