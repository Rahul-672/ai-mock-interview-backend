package com.example.AIMockInterview.repository;

import com.example.AIMockInterview.entity.SessionMessage;
import com.example.AIMockInterview.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SessionMessageRepository extends JpaRepository<SessionMessage, Long>{
    List<SessionMessage> findBySessionOrderById(InterviewSession session);
}