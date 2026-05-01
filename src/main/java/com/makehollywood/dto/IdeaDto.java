package com.makehollywood.dto;

import lombok.Data;

import java.time.LocalDateTime;

public class IdeaDto {

    @Data
    public static class SaveRequest {
        private String source;
        private String idea;
        private String ideaTr;
        private String inputLang;
        private String outputLang;
    }

    @Data
    public static class UpdateRequest {
        private String source;
        private String idea;
    }

    @Data
    public static class MarkUsedRequest {
        private boolean used;
    }

    @Data
    public static class ExtractRequest {
        private String text;
        private String inputLang;
        private String outputLang;
    }

    @Data
    public static class ExtractedIdea {
        private String text;
        private String tr;
    }

    @Data
    public static class Response {
        private Long id;
        private String source;
        private String idea;
        private String ideaTr;
        private String inputLang;
        private String outputLang;
        private boolean used;
        private LocalDateTime createdAt;
    }
}