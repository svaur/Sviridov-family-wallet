package ru.sviridov.wallet.web.dto;

/**
 * Result of re-applying the full rule set to the user's transactions.
 *
 * @param recategorized the number of transactions whose category changed
 */
public record RuleApplyResponse(int recategorized) {
}
