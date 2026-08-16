package com.aedev.myserver.presentation.controller.audio;

import com.aedev.myserver.application.dto.audio.GenerateStoryAudioRequest;
import com.aedev.myserver.application.dto.audio.StoryAudioResponse;
import com.aedev.myserver.application.service.audio.StoryAudioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audio")
public class StoryAudioController {

    private final StoryAudioService storyAudioService;

    public StoryAudioController(StoryAudioService storyAudioService) {
        this.storyAudioService = storyAudioService;
    }

    @PostMapping("/stories")
    public ResponseEntity<StoryAudioResponse> generateStoryAudio(
            @Valid @RequestBody GenerateStoryAudioRequest request
    ) {
        StoryAudioResponse response = storyAudioService.generateOrGetAudio(request);
        return ResponseEntity.ok(response);
    }
}