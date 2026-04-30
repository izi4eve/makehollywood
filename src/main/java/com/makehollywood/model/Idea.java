package com.makehollywood.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ideas")
@Getter @Setter @NoArgsConstructor
public class Idea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String source;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String idea;

    @Column(name = "idea_tr", columnDefinition = "TEXT")
    private String ideaTr;

    @Column(name = "input_lang", length = 10)
    private String inputLang;

    @Column(name = "output_lang", length = 10)
    private String outputLang;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}