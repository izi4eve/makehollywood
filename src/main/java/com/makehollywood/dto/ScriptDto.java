package com.makehollywood.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class ScriptDto {

    @Data
    public static class GenerateRequest {
        private String source;
        private String coreMessage;
        private String inputLang;
        private String outputLang;
    }

    @Data
    public static class RefineRequest {
        private String text;
        private String instruction;
        private String inputLang;
        private String outputLang;
    }

    @Data
    public static class GeneratedVariant {
        private String text;
        private String tr;
        private String name;
    }

    @Data
    public static class SaveRequest {
        private String source;
        private String coreMessage;
        private String name;
        private String fullText;
        private String fullTextTr;
        private String inputLang;
        private String outputLang;
    }

    @Data
    public static class UpdateRequest {
        private String name;
        private String fullText;
    }

    @Data
    public static class Response {
        private Long id;
        private String source;
        private String coreMessage;
        private String name;
        private String fullText;
        private String fullTextTr;
        private String inputLang;
        private String outputLang;
        private boolean used;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
