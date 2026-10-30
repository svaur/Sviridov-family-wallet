package ru.sviridov.wallet.web;

/**
 * Thrown when the card number extracted from a statement header does not match
 * the selected card. Import is blocked — the user must create a card for the
 * extracted number and upload into it.
 */
public class CardMismatchException extends RuntimeException {

    private final String extractedLast4;

    public CardMismatchException(String extractedLast4, String selectedLast4) {
        super("Номер карты в выписке (…" + extractedLast4 + ") не совпадает с выбранной картой (…"
                + selectedLast4 + "). Загрузка заблокирована — создайте новую карту с этим номером "
                + "и загрузите выписку в неё.");
        this.extractedLast4 = extractedLast4;
    }

    public String getExtractedLast4() {
        return extractedLast4;
    }
}
