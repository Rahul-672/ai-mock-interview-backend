package com.example.AIMockInterview.repository;

import com.example.AIMockInterview.entity.InterviewSession;
import com.example.AIMockInterview.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    List<InterviewSession> findByUserOrderByStartedAtDesc(User user);
}

