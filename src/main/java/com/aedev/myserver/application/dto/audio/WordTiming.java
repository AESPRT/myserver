package com.aedev.myserver.application.dto.audio;

public record WordTiming(
        String word,
        long startMs,
        long endMs
) {
}