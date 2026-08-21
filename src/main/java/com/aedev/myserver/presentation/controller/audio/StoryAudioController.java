package com.aedev.myserver.presentation.controller.audio;

import com.aedev.myserver.application.dto.audio.GenerateStoryAudioRequest;
import com.aedev.myserver.application.dto.audio.StoryAudioResponse;
import com.aedev.myserver.application.service.audio.StoryAudioService;
import com.aedev.myserver.domain.enums.StoryAudioStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        StoryAudioResponse response =
                storyAudioService.generateOrGetAudio(request);

        if (
                response.status()
                        == StoryAudioStatus.PROCESSING
        ) {
            return ResponseEntity
                    .accepted()
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stories/{mediaId}")
    public ResponseEntity<StoryAudioResponse> getStoryAudio(
            @PathVariable String mediaId
    ) {
        return ResponseEntity.ok(
                storyAudioService.getByMediaId(mediaId)
        );
    }
}