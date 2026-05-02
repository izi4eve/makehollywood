package com.makehollywood.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makehollywood.dto.ScriptDto;
import com.makehollywood.model.Script;
import com.makehollywood.model.User;
import com.makehollywood.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final GroqClient groqClient;
    private final ScriptRepository scriptRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 2;

    private String buildSystemPrompt(String style, String voice, String outputLang) {
        String styleBlock = switch (style == null ? "flow" : style) {
            case "spark" -> """
            Style: SHORT, PUNCHY sentences. Maximum 8 words per sentence. \
            Escalating intensity — each block hits harder than the previous. \
            Provocative, bold, no softening. Like 4 nails into a coffin: each one deeper.\
            """;
            case "expert" -> """
            Style: Authoritative and confident. Lead with facts and specific data from the input. \
            No emotional appeals — pure credibility and precision.\
            """;
            case "edge" -> """
            Style: Contrarian and sarcastic. Challenge what everyone assumes is true. \
            Dry wit, sharp edges. The viewer should feel slightly provoked.\
            """;
            case "story" -> """
            Style: Personal narrative. Write as if recounting a real experience. \
            Slow burn — build emotional connection before the payoff.\
            """;
            default -> """
            Style: Smooth and conversational. Natural flow, relatable tone. \
            Not too formal, not too aggressive.\
            """;
        };

        String voiceBlock = switch (voice == null ? "neutral" : voice) {
            case "direct" -> "Address the viewer directly as 'you'. Make it personal and immediate.";
            default -> "Use impersonal phrasing. State facts and observations without direct address.";
        };

        return """
        You are a scriptwriter for viral YouTube Shorts, Reels, and TikToks.
        Your task: take the user's raw idea and write 3 distinct short-form video scripts.

        Each script MUST follow this exact structure:
        - HOOK (0-3s): A bold statement, provocative question, or surprising paradox. NO answer yet. Pure intrigue.
        - BRIDGE (3-12s): 1-2 sentences that get closer to the answer without revealing it. Build tension.
        - VALUE (12-24s): The core insight, advice, or revelation. The payoff. Be specific — use only facts/examples from the user's input, never invent data.
        - CTA (24-28s): One simple action or question for the viewer. Never use "subscribe".

        Key rules:
        - The hook and bridge must NOT reveal the main point. The viewer should only get 50% of the answer by the bridge.
        - Each of the 3 scripts must use a different hook angle.
        - Determine the content type from the input (solution, personal story, experiment, myth-busting, secret, mistake, habit, top-list, stats, disagreement, rant) and shape the script accordingly.
        - Never invent statistics, studies, or facts not present in the user's input.
        - Generate a short name for each script (3-6 words, captures the core angle).

        """ + styleBlock + "\n" + voiceBlock + """

        Return ONLY a valid JSON array. No explanation, no markdown, no extra text.

        Format:
        [
          {"name": "...", "text": "...full script text..."},
          {"name": "...", "text": "...full script text..."},
          {"name": "...", "text": "...full script text..."}
        ]
        """;
    }

    private static final String REFINE_SYSTEM_PROMPT = """
        You are a scriptwriter for viral YouTube Shorts, Reels, and TikToks.
        The user has an existing short-form video script and wants to refine it based on their instruction.

        Rules:
        - Apply the user's instruction carefully. Change only what is asked.
        - Keep the Hook → Bridge → Value → CTA structure intact unless instructed otherwise.
        - Never invent new facts or statistics not present in the original script.
        - Return ONLY the refined script text. No explanation, no JSON, no markdown.
        """;

    private static final String TRANSLATE_SYSTEM_PROMPT = """
        You are a translator. Translate the given text into the target language.
        Return ONLY the translated text. No explanation, no extra output.
        """;

    // ── Generate 3 variants ───────────────────────────────────────────────────

    public List<ScriptDto.GeneratedVariant> generate(ScriptDto.GenerateRequest req) {
        String input = buildGenerateInput(req);
        String systemPrompt = buildSystemPrompt(req.getStyle(), req.getVoice(), req.getOutputLang())
                + "\nWrite all scripts in this language: " + req.getOutputLang();

        List<ScriptDto.GeneratedVariant> variants = generateWithRetry(systemPrompt, input);

        if (!req.getInputLang().equals(req.getOutputLang())) {
            for (ScriptDto.GeneratedVariant v : variants) {
                try {
                    String translated = groqClient.chat(
                            TRANSLATE_SYSTEM_PROMPT + "\nTarget language: " + req.getInputLang(),
                            v.getText()
                    );
                    v.setTr(translated.trim());
                } catch (Exception e) {
                    log.warn("Translation failed for variant, skipping tr: {}", e.getMessage());
                }
            }
        }

        return variants;
    }

    private String buildGenerateInput(ScriptDto.GenerateRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Raw description: ").append(req.getSource());
        if (req.getCoreMessage() != null && !req.getCoreMessage().isBlank()) {
            sb.append("\nCore message: ").append(req.getCoreMessage());
        }
        return sb.toString();
    }

    private List<ScriptDto.GeneratedVariant> generateWithRetry(String systemPrompt, String userText) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String raw = groqClient.chat(systemPrompt, userText);
                return parseVariantsJson(raw);
            } catch (IdeaService.ContentModerationException e) {
                throw e;
            } catch (Exception e) {
                lastException = e;
                log.warn("Generate attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
            }
        }
        throw new RuntimeException("Failed to generate scripts after " + MAX_RETRIES + " attempts", lastException);
    }

    private List<ScriptDto.GeneratedVariant> parseVariantsJson(String raw) {
        String lower = raw.toLowerCase();
        if (!raw.trim().startsWith("[") && (
                lower.contains("i'm sorry") || lower.contains("i cannot") ||
                lower.contains("i can't") || lower.contains("inappropriate") ||
                lower.contains("harmful") || lower.contains("against my") ||
                lower.contains("unable to"))) {
            throw new IdeaService.ContentModerationException("AI declined to process this content");
        }

        String cleaned = raw
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();

        try {
            List<Map<String, String>> parsed = objectMapper.readValue(cleaned, new TypeReference<>() {});
            if (parsed.isEmpty()) throw new RuntimeException("AI returned empty variants list");

            return parsed.stream().map(m -> {
                ScriptDto.GeneratedVariant v = new ScriptDto.GeneratedVariant();
                v.setName(m.get("name"));
                v.setText(m.get("text"));
                return v;
            }).collect(Collectors.toList());

        } catch (IdeaService.ContentModerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", cleaned);
            throw new RuntimeException("AI returned unexpected format", e);
        }
    }

    // ── Refine one variant ────────────────────────────────────────────────────

    public ScriptDto.GeneratedVariant refine(ScriptDto.RefineRequest req) {
        String userMessage = "Original script:\n" + req.getText()
                + "\n\nInstruction: " + req.getInstruction();
        String systemPrompt = REFINE_SYSTEM_PROMPT
                + "\nWrite the result in this language: " + req.getOutputLang();

        String refined = groqClient.chat(systemPrompt, userMessage);

        ScriptDto.GeneratedVariant result = new ScriptDto.GeneratedVariant();
        result.setText(refined.trim());

        if (!req.getInputLang().equals(req.getOutputLang())) {
            try {
                String translated = groqClient.chat(
                        TRANSLATE_SYSTEM_PROMPT + "\nTarget language: " + req.getInputLang(),
                        refined.trim()
                );
                result.setTr(translated.trim());
            } catch (Exception e) {
                log.warn("Translation failed for refined variant: {}", e.getMessage());
            }
        }

        return result;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public ScriptDto.Response save(ScriptDto.SaveRequest req, User user) {
        Script script = new Script();
        script.setUser(user);
        script.setSource(req.getSource());
        script.setCoreMessage(req.getCoreMessage());
        script.setName(req.getName());
        script.setFullText(req.getFullText());
        script.setFullTextTr(req.getFullTextTr());
        script.setInputLang(req.getInputLang());
        script.setOutputLang(req.getOutputLang());
        return toResponse(scriptRepository.save(script));
    }

    public List<ScriptDto.Response> getAll(User user) {
        return scriptRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ScriptDto.Response update(Long id, ScriptDto.UpdateRequest req, User user) {
        Script script = scriptRepository.findById(id)
                .filter(s -> s.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Script not found"));
        script.setName(req.getName());
        script.setFullText(req.getFullText());
        script.setUpdatedAt(LocalDateTime.now());
        return toResponse(scriptRepository.save(script));
    }

    public void delete(Long id, User user) {
        Script script = scriptRepository.findById(id)
                .filter(s -> s.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Script not found"));
        scriptRepository.delete(script);
    }

    private ScriptDto.Response toResponse(Script s) {
        ScriptDto.Response r = new ScriptDto.Response();
        r.setId(s.getId());
        r.setSource(s.getSource());
        r.setCoreMessage(s.getCoreMessage());
        r.setName(s.getName());
        r.setFullText(s.getFullText());
        r.setFullTextTr(s.getFullTextTr());
        r.setInputLang(s.getInputLang());
        r.setOutputLang(s.getOutputLang());
        r.setUsed(s.isUsed());
        r.setCreatedAt(s.getCreatedAt());
        r.setUpdatedAt(s.getUpdatedAt());
        return r;
    }
}
