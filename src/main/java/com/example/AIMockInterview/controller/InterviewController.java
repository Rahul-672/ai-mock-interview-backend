package com.example.AIMockInterview.controller;

import com.example.AIMockInterview.dto.*;
import com.example.AIMockInterview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.IOException;


import java.util.List;
import java.util.Map;

import static org.apache.pdfbox.pdmodel.PDDocument.*;

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


    @PostMapping("/start-with-resume")
    public ResponseEntity<InterviewResponse> startWithResume(
            @RequestBody ResumeInterviewRequest request) {
        return ResponseEntity.ok(
                interviewService.startInterviewWithResume(request));
    }

    @PostMapping(value = "/start-with-resume-file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InterviewResponse> startWithResumeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("difficulty") String difficulty) throws IOException {

        String resumeText = "";
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            resumeText = stripper.getText(document);
        }

        ResumeInterviewRequest request = new ResumeInterviewRequest();
        request.setDifficulty(difficulty);
        request.setResumeText(resumeText);

        return ResponseEntity.ok(
                interviewService.startInterviewWithResume(request));
    }
}