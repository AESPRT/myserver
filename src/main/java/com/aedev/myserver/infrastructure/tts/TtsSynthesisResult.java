package com.aedev.myserver.infrastructure.tts;

import com.aedev.myserver.application.dto.audio.WordTiming;

import java.util.List;

public record TtsSynthesisResult(
        byte[] audioBytes,
        List<WordTiming> wordTimings
) {
}