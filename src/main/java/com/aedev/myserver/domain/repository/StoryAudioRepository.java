package com.aedev.myserver.domain.repository;

import com.aedev.myserver.domain.entity.StoryAudio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoryAudioRepository extends JpaRepository<StoryAudio, Long> {

    Optional<StoryAudio> findByMediaId(String mediaId);

    Optional<StoryAudio> findByTitleIgnoreCase(String title);

    Optional<StoryAudio> findByContentHash(String contentHash);
}