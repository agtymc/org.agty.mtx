package org.agty.mtx.dto;

public class StreamChunkDto {
    private String type;
    private String content;
    private String error;

    public StreamChunkDto() {
    }

    public StreamChunkDto(String type, String content, String error) {
        this.type = type;
        this.content = content;
        this.error = error;
    }

    public static StreamChunkDto chunk(String content) {
        return new StreamChunkDto("chunk", content, null);
    }

    public static StreamChunkDto done() {
        return new StreamChunkDto("done", null, null);
    }

    public static StreamChunkDto error(String error) {
        return new StreamChunkDto("error", null, error);
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getError() {
        return error;
    }
}

