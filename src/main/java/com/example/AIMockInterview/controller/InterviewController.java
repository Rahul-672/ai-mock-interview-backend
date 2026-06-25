package com.example.AIMockInterview.controller;

import com.example.AIMockInterview.dto.*;
import com.example.AIMockInterview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/start")
    public ResponseEntity<InterviewResponse> startInterview(
            @RequestBody StartInterviewRequest request) {
        return ResponseEntity.ok(interviewService.startInterview(request));
    }

    @PostMapping("/answer")
    public ResponseEntity<InterviewResponse> submitAnswer(
            @RequestBody AnswerRequest request) {
        return ResponseEntity.ok(interviewService.submitAnswer(request));
    }

    @GetMapping("/session/{sessionId}/messages")
    public ResponseEntity<List<Map<String, Object>>> getSessionMessages(
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewService.getSessionMessages(sessionId));
    }
}