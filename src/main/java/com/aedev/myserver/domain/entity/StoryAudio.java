package com.aedev.myserver.domain.entity;

import com.aedev.myserver.application.dto.audio.WordTiming;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "story_audio",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_story_audio_media_id", columnNames = "media_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryAudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_id", nullable = false, unique = true)
    private String mediaId;

    @Column(nullable = false)
    private String title;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(nullable = false)
    private String url;

    @Column(name = "voice_id")
    private String voiceId;

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "character_count")
    private Integer characterCount;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_hash")
    private String contentHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "word_timings",
            columnDefinition = "jsonb",
            nullable = false
    )
    private List<WordTiming> wordTimings = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}