package com.example.AIMockInterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterviewResponse{
    private Long sessionId;
    private String message;
    private Integer questionNumber;
    private Integer totalQuestions;
    private Double score;
    private String status;
    private String feedbackReport;
}