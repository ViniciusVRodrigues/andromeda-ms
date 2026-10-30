package andromeda.sei.ms.feature.aiusage.domain;

/** Interactions of a set of users with one editor (a {@code null} editor is its own row), summed in the database. */
public record AiUsageEditorTotal(String editor, long interactions) {}
