package com.toastedvr.toastedvr.backend.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.transformer.splitter.TextSplitter;

// Spring AI's built-in TokenTextSplitter splits by token count and has no
// overlap support. This does plain character-based sliding-window chunking
// (target size with overlap) instead, which is what the ingestion spec asks
// for and is simple enough not to need a third-party splitter.
class CharacterTextSplitter extends TextSplitter {

    private static final int CHUNK_SIZE_CHARS = 700;
    private static final int CHUNK_OVERLAP_CHARS = 100;

    @Override
    protected List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        int step = CHUNK_SIZE_CHARS - CHUNK_OVERLAP_CHARS;
        int length = text.length();

        for (int start = 0; start < length; start += step) {
            int end = Math.min(start + CHUNK_SIZE_CHARS, length);
            String chunk = text.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == length) {
                break;
            }
        }

        return chunks;
    }
}
