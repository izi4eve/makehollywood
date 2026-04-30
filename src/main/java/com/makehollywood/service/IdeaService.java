package com.makehollywood.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makehollywood.dto.IdeaDto;
import com.makehollywood.model.Idea;
import com.makehollywood.model.User;
import com.makehollywood.repository.IdeaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdeaService {

    private final GroqClient groqClient;
    private final IdeaRepository ideaRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 2;

    private static final String EXTRACT_SYSTEM_PROMPT = """
        You are an assistant that helps content creators shape their raw thoughts into clear video ideas for short-form content (YouTube Shorts, Reels, TikTok).

        Your job:
        1. Read the user's raw text (it may be in any language, possibly messy or unstructured).
        2. Identify all distinct ideas hidden in the text. One dump may contain several unrelated thoughts — split them.
        3. For each idea, determine which of the following content types fits best (one idea can match 1-2 types):
           - SOLUTION: An interesting, new, or effective solution to a common problem
           - EXPERIENCE: A useful personal experience from life, work, or hobby
           - MYTH: Expose a lie or common misconception with arguments and a useful tip
           - SECRET: A secret that only a specific audience knows and nobody talks about
           - MISTAKES: The most common mistakes in some area, with effective solutions
           - HABIT: A habit or rule people follow that prevents them from improving
           - TOP: A top list of tips with reasons why
           - STATS: Interesting and unexpected statistics, research results, or new products with your conclusions
           - EXPERIMENT: Your experiment doing something differently from everyone else and what happened over time
           - DISAGREE: A common rule you disagree with and why, with a personal example
           - RANT: What surprised or bothered you recently, why, what conclusions you drew, what solution you see

        4. For each identified idea, write a clear and complete description. The description must:
           - State the core useful insight (no fluff, no hooks, no marketing)
           - Be self-contained — enough for an AI to later write a full short-form script from it
           - Include only what the user actually said — do NOT invent statistics, studies, or facts
           - Do NOT include any content type label or prefix (no "PERSONAL EXPERIENCE:", "SOLUTION:", etc.) — just the idea description itself

        5. Return ONLY a valid JSON array. No explanation, no markdown, no extra text.

        Format:
        [
           {"text": "...idea description..."},
           {"text": "...idea description..."}
        ]
        """;

    private static final String TRANSLATE_SYSTEM_PROMPT = """
        You are a translator. Translate the given English text into the target language.
        Return ONLY the translated text. No explanation, no extra output.
        """;

    public List<IdeaDto.ExtractedIdea> extract(String rawText, String inputLang, String outputLang) {
        String fullPrompt = EXTRACT_SYSTEM_PROMPT + "\nWrite all idea descriptions in this language: " + outputLang;

        // Step 1: extract with retry
        List<IdeaDto.ExtractedIdea> ideas = extractWithRetry(fullPrompt, rawText);

        // Step 2: translate back if needed
        if (!inputLang.equals(outputLang)) {
            for (IdeaDto.ExtractedIdea idea : ideas) {
                try {
                    String translated = groqClient.chat(
                            TRANSLATE_SYSTEM_PROMPT + "\nTarget language: " + inputLang,
                            idea.getText()
                    );
                    idea.setTr(translated.trim());
                } catch (Exception e) {
                    log.warn("Translation failed for idea, skipping tr: {}", e.getMessage());
                    // tr остаётся null — фронт просто не покажет перевод
                }
            }
        }

        return ideas;
    }

    private List<IdeaDto.ExtractedIdea> extractWithRetry(String systemPrompt, String userText) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String raw = groqClient.chat(systemPrompt, userText);
                return parseIdeasJson(raw);
            } catch (ContentModerationException e) {
                // Модерация — не ретраить, сразу пробросить
                throw e;
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
            }
        }

        throw new RuntimeException("Failed to extract ideas after " + MAX_RETRIES + " attempts", lastException);
    }

    private List<IdeaDto.ExtractedIdea> parseIdeasJson(String raw) {
        // Проверяем, не вернула ли модель отказ вместо JSON
        String lower = raw.toLowerCase();
        if (!raw.trim().startsWith("[") && (
                lower.contains("i'm sorry") ||
                        lower.contains("i cannot") ||
                        lower.contains("i can't") ||
                        lower.contains("inappropriate") ||
                        lower.contains("harmful") ||
                        lower.contains("against my") ||
                        lower.contains("unable to"))) {
            throw new ContentModerationException("AI declined to process this content");
        }

        String cleaned = raw
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();

        try {
            List<Map<String, String>> parsed = objectMapper.readValue(
                    cleaned, new TypeReference<>() {});

            if (parsed.isEmpty()) {
                throw new RuntimeException("AI returned empty ideas list");
            }

            return parsed.stream().map(m -> {
                IdeaDto.ExtractedIdea idea = new IdeaDto.ExtractedIdea();
                idea.setText(m.get("text"));
                return idea;
            }).collect(Collectors.toList());

        } catch (ContentModerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", cleaned);
            throw new RuntimeException("AI returned unexpected format", e);
        }
    }

    // Отдельный класс исключения для модерации
    public static class ContentModerationException extends RuntimeException {
        public ContentModerationException(String message) {
            super(message);
        }
    }

    public IdeaDto.Response save(IdeaDto.SaveRequest req, User user) {
        Idea idea = new Idea();
        idea.setUser(user);
        idea.setSource(req.getSource());
        idea.setIdea(req.getIdea());
        idea.setIdeaTr(req.getIdeaTr());
        idea.setInputLang(req.getInputLang());
        idea.setOutputLang(req.getOutputLang());
        return toResponse(ideaRepository.save(idea));
    }

    public List<IdeaDto.Response> getAll(User user) {
        return ideaRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public IdeaDto.Response update(Long id, IdeaDto.UpdateRequest req, User user) {
        Idea idea = ideaRepository.findById(id)
                .filter(i -> i.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Idea not found"));
        idea.setSource(req.getSource());
        idea.setIdea(req.getIdea());
        return toResponse(ideaRepository.save(idea));
    }

    public void delete(Long id, User user) {
        Idea idea = ideaRepository.findById(id)
                .filter(i -> i.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Idea not found"));
        ideaRepository.delete(idea);
    }

    private IdeaDto.Response toResponse(Idea idea) {
        IdeaDto.Response r = new IdeaDto.Response();
        r.setId(idea.getId());
        r.setSource(idea.getSource());
        r.setIdea(idea.getIdea());
        r.setIdeaTr(idea.getIdeaTr());
        r.setInputLang(idea.getInputLang());
        r.setOutputLang(idea.getOutputLang());
        r.setCreatedAt(idea.getCreatedAt());
        return r;
    }
}