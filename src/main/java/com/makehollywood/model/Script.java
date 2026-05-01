package com.makehollywood.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "scripts")
@Getter @Setter @NoArgsConstructor
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String source;

    @Column(name = "core_message", columnDefinition = "TEXT")
    private String coreMessage;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String fullText;

    @Column(name = "full_text_tr", columnDefinition = "TEXT")
    private String fullTextTr;

    @Column(length = 255)
    private String name;

    @Column(name = "input_lang", length = 10)
    private String inputLang;

    @Column(name = "output_lang", length = 10)
    private String outputLang;

    private boolean used = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
