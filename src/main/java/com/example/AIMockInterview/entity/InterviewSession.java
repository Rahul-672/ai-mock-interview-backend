package com.example.AIMockInterview.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name="interview_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewSession{

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long Id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String role;
    private String difficulty;

    private Integer totalQuestions;
    private Integer currentQuestion;
    private Double overallScore;

    @Column(columnDefinition = "TEXT")
    private String feedbackReport;

    private String status;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @PrePersist
    public void prePersis(){
        this.startedAt = LocalDateTime.now();
        this.status = "ACTIVE";
        this.currentQuestion = 0;
    }
}