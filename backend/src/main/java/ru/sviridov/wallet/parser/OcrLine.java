package ru.sviridov.wallet.parser;

/**
 * A single line of text produced by OCR, with normalised coordinates.
 *
 * @param text the recognised text of the line
 * @param x    left edge as a fraction of the image width (0..1)
 * @param y    top edge as a fraction of the image height (0 = top, 1 = bottom)
 */
public record OcrLine(String text, float x, float y) {
}
