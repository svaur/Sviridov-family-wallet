package ru.sviridov.wallet.parser;

/**
 * Replaceable statement parser. Implementations turn raw PDF bytes into a
 * structured {@link ParsedStatement}. Kept behind an interface so the concrete
 * PDFBox logic can be swapped without touching the import pipeline.
 */
public interface StatementParser {

    /**
     * Parses a bank statement PDF.
     *
     * @param pdfBytes raw PDF bytes (never stored)
     * @return structured statement
     * @throws StatementParseException if the PDF cannot be read or is not a statement
     */
    ParsedStatement parse(byte[] pdfBytes) throws StatementParseException;

    /**
     * Parser version, persisted as {@code statements.parser_version}.
     */
    default String version() {
        return "1.0";
    }
}
