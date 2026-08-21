package com.aedev.myserver.infrastructure.tts;

import java.util.List;

public record ForcedAlignmentResponse(
        List<AlignedCharacter> characters,
        List<AlignedWord> words,
        Double loss
) {

    public record AlignedCharacter(
            String text,
            Double start,
            Double end
    ) {}

    public record AlignedWord(
            String text,
            Double start,
            Double end,
            Double loss
    ) {}
}