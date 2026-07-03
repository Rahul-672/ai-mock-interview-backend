package com.example.AIMockInterview.service;

import com.example.AIMockInterview.dto.*;
import com.example.AIMockInterview.entity.*;
import com.example.AIMockInterview.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import org.springframework.cache.annotation.Cacheable;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final ChatClient.Builder chatClientBuilder;
    private final InterviewSessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final UserRepository userRepository;

    private static final int TOTAL_QUESTIONS = 5;
    @CacheEvict(value = "userHistory", key = "#root.target.getCurrentUserEmail()")
    public InterviewResponse startInterview(StartInterviewRequest request) {
        User user = getCurrentUser();

        InterviewSession session = InterviewSession.builder()
                .user(user)
                .role(request.getRole())
                .difficulty(request.getDifficulty())
                .totalQuestions(TOTAL_QUESTIONS)
                .build();
        session = sessionRepository.save(session);

        String firstQuestion = askAiForQuestion(session, null, 1);
        saveMessage(session, "assistant", firstQuestion, 1, null);

        session.setCurrentQuestion(1);
        sessionRepository.save(session);

        return InterviewResponse.builder()
                .sessionId(session.getId())
                .message(firstQuestion)
                .questionNumber(1)
                .totalQuestions(TOTAL_QUESTIONS)
                .status("ACTIVE")
                .build();
    }

    public InterviewResponse submitAnswer(AnswerRequest request) {
        InterviewSession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new RuntimeException("Session not found"));

        int currentQ = session.getCurrentQuestion();
        saveMessage(session, "user", request.getAnswer(), currentQ, null);

        // Check if interview is done
        if (currentQ >= TOTAL_QUESTIONS) {
            double score = evaluateAnswer(session, request.getAnswer(), currentQ);
            updateScore(session, currentQ, score);
            return completeInterview(session);
        }

        // Run evaluation AND next question generation in parallel
        CompletableFuture<Double> scoreFuture = CompletableFuture
                .supplyAsync(() -> evaluateAnswer(session, request.getAnswer(), currentQ),
                        getAsyncExecutor());

        CompletableFuture<String> nextQuestionFuture = CompletableFuture
                .supplyAsync(() -> askAiForQuestion(session, request.getAnswer(), currentQ + 1),
                        getAsyncExecutor());

        // Wait for both to complete
        CompletableFuture.allOf(scoreFuture, nextQuestionFuture).join();

        double score = scoreFuture.join();
        String nextQuestion = nextQuestionFuture.join();

        updateScore(session, currentQ, score);
        saveMessage(session, "assistant", nextQuestion, currentQ + 1, null);

        session.setCurrentQuestion(currentQ + 1);
        sessionRepository.save(session);

        return InterviewResponse.builder()
                .sessionId(session.getId())
                .message(nextQuestion)
                .questionNumber(currentQ + 1)
                .totalQuestions(TOTAL_QUESTIONS)
                .score(score)
                .status("ACTIVE")
                .build();
    }

    // Helper method to get executor
    private Executor getAsyncExecutor() {
        return new ThreadPoolTaskExecutor() {{
            setCorePoolSize(5);
            setMaxPoolSize(10);
            initialize();
        }};
    }

    // Helper to update score
    private void updateScore(InterviewSession session, int questionNumber, double score) {
        List<SessionMessage> messages = messageRepository.findBySessionOrderById(session);
        messages.stream()
                .filter(m -> m.getRole().equals("user") && m.getQuestionNumber() == questionNumber)
                .findFirst()
                .ifPresent(msg -> {
                    msg.setScore(score);
                    messageRepository.save(msg);
                });
    }

    private InterviewResponse completeInterview(InterviewSession session) {
        String report = generateFeedbackReport(session);

        List<SessionMessage> allMessages = messageRepository.findBySessionOrderById(session);
        double avgScore = allMessages.stream()
                .filter(m -> m.getRole().equals("user") && m.getScore() != null)
                .mapToDouble(SessionMessage::getScore)
                .average()
                .orElse(0.0);

        session.setStatus("COMPLETED");
        session.setCompletedAt(LocalDateTime.now());
        session.setFeedbackReport(report);
        session.setOverallScore(avgScore);
        sessionRepository.save(session);

        return InterviewResponse.builder()
                .sessionId(session.getId())
                .message("Interview complete! Here is your feedback.")
                .questionNumber(TOTAL_QUESTIONS)
                .totalQuestions(TOTAL_QUESTIONS)
                .score(avgScore)
                .status("COMPLETED")
                .feedbackReport(report)
                .build();
    }

    private String askAiForQuestion(InterviewSession session,
                                    String previousAnswer, int questionNumber) {

        String context = previousAnswer != null
                ? "The candidate just answered: \"" + previousAnswer + "\". " +
                "Ask a follow-up question or move to a related topic."
                : "Start the interview with your first question.";

        String prompt = """
            You are a technical interviewer conducting a %s interview.
            Difficulty: %s.
            This is question %d of %d.
            %s
            
            RULES:
            - Ask ONE clear, specific technical question
            - No preamble like "Great answer!" or "That's interesting"
            - No multi-part questions
            - No explanations or hints
            - Just the question, nothing else
            - Match the difficulty level strictly
            - Do NOT use markdown formatting like ###, **, or bullet points
            - Plain text only
            """.formatted(
                session.getRole(),
                session.getDifficulty(),
                questionNumber,
                TOTAL_QUESTIONS,
                context
        );

        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content()
                .trim();
    }

    private double evaluateAnswer(InterviewSession session,
                                  String answer, int questionNumber) {

        // Immediate low score for clearly bad answers
        if (answer == null || answer.trim().isEmpty()) return 0.0;
        if (answer.trim().length() < 10) return 5.0;
        if (answer.trim().split("\\s+").length < 3) return 10.0;

        List<SessionMessage> messages = messageRepository.findBySessionOrderById(session);
        String lastQuestion = messages.stream()
                .filter(m -> m.getRole().equals("assistant")
                        && m.getQuestionNumber() == questionNumber)
                .map(SessionMessage::getContent)
                .findFirst().orElse("Unknown question");

        String prompt = """
            You are a strict technical interviewer evaluating a candidate for a %s role.
            Difficulty level: %s.
            
            Question asked: "%s"
            Candidate's answer: "%s"
            
            Score the answer using these criteria:
            1. Technical accuracy — is the answer correct? (40 points)
            2. Relevance — does it actually address the question? (30 points)
            3. Completeness — is it sufficiently detailed? (20 points)
            4. Clarity — is it well structured? (10 points)
            
            STRICT SCORING RULES:
            - Gibberish, random text, or completely unrelated answer → score 0 to 10
            - Answer that mentions the topic but is mostly wrong → score 10 to 30
            - Partially correct answer with some key points → score 30 to 60
            - Mostly correct answer with minor gaps → score 60 to 80
            - Complete, accurate, well-explained answer → score 80 to 100
            - One word or one sentence answers → never above 25
            - If the answer does not match the question at all → score below 15
            
            Respond with ONLY a single integer number between 0 and 100.
            No explanation, no text, just the number.
            """.formatted(session.getRole(), session.getDifficulty(),
                lastQuestion, answer);

        String result = chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content()
                .trim();

        try {
            double score = Double.parseDouble(result.replaceAll("[^0-9.]", ""));
            // Clamp between 0 and 100
            return Math.max(0, Math.min(100, score));
        } catch (NumberFormatException e) {
            return 50.0;
        }
    }

    public List<Map<String, Object>> getSessionMessages(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        return messageRepository.findBySessionOrderById(session)
                .stream()
                .map(m -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("role", m.getRole());
                    map.put("content", m.getContent());
                    map.put("questionNumber", m.getQuestionNumber());
                    map.put("score", m.getScore());
                    return map;
                })
                .toList();
    }

    @Cacheable(value = "userHistory", key = "#root.target.getCurrentUserEmail()")
    public List<Map<String, Object>> getUserHistory() {
        User user = getCurrentUser();
        List<InterviewSession> sessions = sessionRepository
                .findByUserOrderByStartedAtDesc(user);

        return sessions.stream()
                .filter(s -> s.getStatus().equals("COMPLETED"))
                .map(s -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("sessionId", s.getId());
                    map.put("role", s.getRole());
                    map.put("difficulty", s.getDifficulty());
                    map.put("overallScore", s.getOverallScore());
                    map.put("startedAt", s.getStartedAt().toString());
                    map.put("feedbackReport", s.getFeedbackReport());
                    return map;
                })
                .toList();
    }

    private String generateFeedbackReport(InterviewSession session) {
        List<SessionMessage> messages = messageRepository.findBySessionOrderById(session);

        StringBuilder conversation = new StringBuilder();
        for (SessionMessage msg : messages) {
            if (msg.getRole().equals("assistant")) {
                conversation.append("Q").append(msg.getQuestionNumber())
                        .append(": ").append(msg.getContent()).append("\n");
            } else {
                conversation.append("Answer: ").append(msg.getContent())
                        .append("\n");
                if (msg.getScore() != null) {
                    conversation.append("Score given: ").append(msg.getScore())
                            .append("/100\n\n");
                }
            }
        }

        String prompt = """
            You are a senior technical interviewer who just completed a %s interview
            at %s difficulty level with a candidate.
            
            Here is the complete interview transcript with scores:
            %s
            
            Based on this transcript, generate a detailed, honest feedback report.
            
            IMPORTANT RULES FOR FEEDBACK:
            - Be honest and critical — do not give positive feedback for wrong answers
            - If the candidate gave gibberish or off-topic answers, mention it clearly
            - Identify specific technical gaps based on what they got wrong
            - Model answers should be concise but technically accurate
            - Topics to study should be specific (e.g. "HashMap internal hashing" not just "Collections")
            - Summary should be an honest 2-3 sentence overall assessment
            
            Generate the report in this EXACT JSON format with no markdown, no backticks:
            {
              "overallScore": <calculate average of the scores given above>,
              "strengths": [
                "<specific strength observed from their answers>",
                "<another specific strength>"
              ],
              "weaknesses": [
                "<specific weakness or gap observed>",
                "<another specific weakness>"
              ],
              "topicsToStudy": [
                "<specific topic they need to study>",
                "<another specific topic>"
              ],
              "questionFeedback": [
                {
                  "question": "<exact question asked>",
                  "yourAnswer": "<brief summary of what candidate said>",
                  "modelAnswer": "<what a correct answer should include>",
                  "score": <score given for this answer>
                }
              ],
              "summary": "<honest 2-3 sentence overall assessment of the candidate>"
            }
            
            Respond with ONLY the JSON object. No markdown. No backticks. No explanation.
            """.formatted(session.getRole(), session.getDifficulty(), conversation);

        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content()
                .trim();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void saveMessage(InterviewSession session, String role,
                             String content, int questionNumber, Double score) {
        messageRepository.save(SessionMessage.builder()
                .session(session)
                .role(role)
                .content(content)
                .questionNumber(questionNumber)
                .score(score)
                .build());
    }


    @CacheEvict(value = "userHistory", key = "#root.target.getCurrentUserEmail()")
    public InterviewResponse startInterviewWithResume(
            ResumeInterviewRequest request) {
        User user = getCurrentUser();

        InterviewSession session = InterviewSession.builder()
                .user(user)
                .role("Resume Based")
                .difficulty(request.getDifficulty())
                .totalQuestions(TOTAL_QUESTIONS)
                .build();
        session = sessionRepository.save(session);

        String firstQuestion = askAiForQuestionFromResume(
                session, request.getResumeText(), 1);

        saveMessage(session, "assistant", firstQuestion, 1, null);
        session.setCurrentQuestion(1);
        sessionRepository.save(session);

        return InterviewResponse.builder()
                .sessionId(session.getId())
                .message(firstQuestion)
                .questionNumber(1)
                .totalQuestions(TOTAL_QUESTIONS)
                .status("ACTIVE")
                .build();
    }

    public String getCurrentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private String askAiForQuestionFromResume(
            InterviewSession session, String resumeText, int questionNumber) {

        String prompt = """
            You are a technical interviewer conducting a personalized interview.
            Difficulty: %s.
            This is question %d of %d.
            
            The candidate's resume content is:
            "%s"
            
            Based on the resume, ask ONE specific technical question about:
            - Their mentioned skills or technologies
            - Their projects or experience
            - Concepts related to their background
            
            RULES:
            - Ask ONE clear, specific question only
            - No preamble or markdown
            - Base the question on actual resume content
            - Match the difficulty level
            """.formatted(
                session.getDifficulty(),
                questionNumber,
                TOTAL_QUESTIONS,
                resumeText.length() > 2000
                        ? resumeText.substring(0, 2000)
                        : resumeText
        );

        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content()
                .trim();
    }
}