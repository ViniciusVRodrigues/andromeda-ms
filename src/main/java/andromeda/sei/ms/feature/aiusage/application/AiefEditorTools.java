package andromeda.sei.ms.feature.aiusage.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which agent tool an AIEF row belongs to (agente-endpoint-v2, design 6): a fixed, EXACT table from the
 * {@code editor} field to the agent {@code tool}. Any other editor (VS Code, Cursor, Copilot CLI, Claude
 * Desktop, Unknown, blank, null) has no tool. The model never identifies the tool.
 * Configured as {@code editor=tool} pairs separated by commas in {@code andromeda.agent.aief-editor-tools}.
 */
@Component
public class AiefEditorTools {

    public static final String DEFAULT =
            "Claude (VS Code)=claude-code,Claude Code CLI=claude-code,Claude Code=claude-code,Codex CLI=codex";

    private final Map<String, String> toolByEditor = new LinkedHashMap<>();

    public AiefEditorTools(@Value("${andromeda.agent.aief-editor-tools:" + DEFAULT + "}") String table) {
        for (String pair : (table == null || table.isBlank() ? DEFAULT : table).split(",")) {
            int eq = pair.lastIndexOf('=');
            if (eq <= 0 || eq == pair.length() - 1) continue;
            toolByEditor.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
    }

    public static AiefEditorTools defaults() {
        return new AiefEditorTools(DEFAULT);
    }

    /** The agent tool of the AIEF editor, or null when it has none (exact match). */
    public String toolOf(String editor) {
        return editor == null ? null : toolByEditor.get(editor.trim());
    }

    /** The AIEF editors that stand for this agent tool; empty when none or the tool is null. */
    public List<String> editorsOf(String tool) {
        List<String> editors = new ArrayList<>();
        if (tool == null) return editors;
        toolByEditor.forEach((editor, t) -> {
            if (t.equals(tool)) editors.add(editor);
        });
        return editors;
    }

    /** Unmapped editor that still looks like one of the agent tools (a new label of the extension). */
    public static boolean looksLikeAgentTool(String editor) {
        return editor != null && (editor.startsWith("Claude") || editor.startsWith("Codex"));
    }

    public Set<String> editors() {
        return toolByEditor.keySet();
    }
}
