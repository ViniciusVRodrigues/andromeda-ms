package andromeda.sei.ms.feature.usage.application.export;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Minimal RFC 4180 writer (comma, CRLF, double-quote escaping) with a UTF-8 BOM helper and spreadsheet
 * formula-injection protection: a cell starting with {@code = + - @ TAB CR} gets a leading apostrophe.
 */
public final class CsvWriter {

    private static final char BOM = '﻿';

    private final Writer out;

    public CsvWriter(Writer out) {
        this.out = out;
    }

    /** Writes the UTF-8 byte order mark; call once, before the header. */
    public void writeBom() throws IOException {
        out.write(BOM);
    }

    public void writeRow(List<String> cells) throws IOException {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) row.append(',');
            row.append(escape(cells.get(i)));
        }
        row.append("\r\n");
        out.write(row.toString());
    }

    public void flush() throws IOException {
        out.flush();
    }

    static String escape(String cell) {
        if (cell == null || cell.isEmpty()) return "";
        String value = cell;
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            value = "'" + value;
        }
        boolean needsQuotes = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
        if (!needsQuotes) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
