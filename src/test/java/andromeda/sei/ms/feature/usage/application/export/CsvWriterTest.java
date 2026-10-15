package andromeda.sei.ms.feature.usage.application.export;

import andromeda.sei.ms.feature.usage.application.usecase.ExportUsageEventsUseCase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    private static String row(String... cells) throws IOException {
        StringWriter w = new StringWriter();
        new CsvWriter(w).writeRow(Arrays.asList(cells));
        return w.toString();
    }

    @Test
    void should_writeBomHeaderAndCrlf() throws IOException {
        StringWriter w = new StringWriter();
        CsvWriter csv = new CsvWriter(w);
        csv.writeBom();
        csv.writeRow(ExportUsageEventsUseCase.COLUMNS);
        assertThat(w.toString()).isEqualTo("﻿occurred_at,received_at,user_id,email,type,screen,visible_ms,"
                + "element,label,area,action,value,viewport,session_id,view_id,origin,status,code,flow,stage,step,"
                + "funnel_id\r\n");
    }

    @Test
    void should_leavePlainCellsAndEmptyNulls() throws IOException {
        assertThat(row("a", null, "", "b c")).isEqualTo("a,,,b c\r\n");
    }

    @Test
    void should_quoteCommaQuoteAndLineBreaks() throws IOException {
        assertThat(row("a,b")).isEqualTo("\"a,b\"\r\n");
        assertThat(row("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"\r\n");
        assertThat(row("l1\nl2")).isEqualTo("\"l1\nl2\"\r\n");
        assertThat(row("l1\r\nl2")).isEqualTo("\"l1\r\nl2\"\r\n");
    }

    @Test
    void should_prefixFormulaStarters() throws IOException {
        assertThat(row("=SUM(A1)")).isEqualTo("'=SUM(A1)\r\n");
        assertThat(row("+1")).isEqualTo("'+1\r\n");
        assertThat(row("-1")).isEqualTo("'-1\r\n");
        assertThat(row("@x")).isEqualTo("'@x\r\n");
        assertThat(row("\tx")).isEqualTo("'\tx\r\n");
        assertThat(row("\rx")).isEqualTo("\"'\rx\"\r\n");
    }

    @Test
    void should_prefixAndQuoteFormulaWithQuotes() throws IOException {
        assertThat(row("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"\r\n");
        assertThat(row("a=b")).isEqualTo("a=b\r\n");
    }
}
