package com.example.AIMockInterview.entity;


import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "session_message")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder

public class SessionMessage{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private InterviewSession session;

    private String role;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Integer questionNumber;
    private Double score;
}