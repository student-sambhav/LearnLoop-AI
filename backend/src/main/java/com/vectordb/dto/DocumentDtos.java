package com.vectordb.dto;

import java.util.List;

public final class DocumentDtos {

    private DocumentDtos() {}

    public record DocInsertRequest(String title, String text) {}

    public record DocInsertResponse(List<Integer> ids, int chunks, int dims) {}

    public record DocListItem(int id, String title, String preview, int words) {}

    public record DocSearchRequest(String question, Integer k) {}

    public record ContextDto(int id, String title, float distance) {}

    public record DocSearchResponse(List<ContextDto> contexts) {}

    public record AskRequest(String question, Integer k) {}

    public record AskContextDto(int id, String title, String text, float distance) {}

    public record AskResponse(String answer, String model, List<AskContextDto> contexts, int docCount) {}

    public record DeleteResponse(boolean ok) {}

    public record StatusResponse(boolean ollamaAvailable, String embedModel, String genModel,
                                  int docCount, int docDims, int demoDims, int demoCount) {}
}
