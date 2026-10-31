/** Detects whether a string looks like a regular expression (contains regex metacharacters). */
export function isRegex(value: string): boolean {
  return /[\\^$*+?()[\]{}|]/.test(value)
}

/** The flat, optional conditions a rule can carry. */
export interface RuleConditions {
  purpose: string
  operation: string
  min: string
  max: string
  dayFrom: string
  dayTo: string
}

/**
 * Builds the {@code params} JSON object sent to the backend from the flat form
 * fields. "Назначение платежа" and "Операция" auto-detect regular expressions:
 * a value with regex metacharacters becomes {@code purpose_regex} /
 * {@code operation_regex}, otherwise {@code purpose_contains} /
 * {@code operation_contains}. Empty fields are omitted.
 */
export function buildRuleParams(f: RuleConditions): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  const purpose = f.purpose.trim()
  if (purpose !== '') {
    params[isRegex(purpose) ? 'purpose_regex' : 'purpose_contains'] = purpose
  }
  const operation = f.operation.trim()
  if (operation !== '') {
    params[isRegex(operation) ? 'operation_regex' : 'operation_contains'] = operation
  }
  if (f.min !== '') params.min = Number(f.min)
  if (f.max !== '') params.max = Number(f.max)
  if (f.dayFrom !== '') params.day_from = Number(f.dayFrom)
  if (f.dayTo !== '') params.day_to = Number(f.dayTo)
  return params
}

/** Lowercases and collapses whitespace — mirrors the backend TextNormalizer. */
function normalizeCore(value: string): string {
  return value.toLowerCase().replace(/\s+/g, ' ').trim()
}

function regexFind(pattern: string, haystack: string): boolean {
  try {
    return new RegExp(pattern, 'iu').test(haystack)
  } catch {
    return false
  }
}

/** The transaction fields a rule is matched against (subset used by the quick form). */
export interface RuleTemplate {
  purpose: string | null
  operation: string | null
  amount: number | null
  date: string | null
}

/** Day of month from an ISO date ("YYYY-MM-DD" → 16), or null when absent. */
export function dayOfMonth(date: string | null): number | null {
  if (!date) return null
  const parts = date.split('-')
  return parts.length === 3 ? Number(parts[2]) : null
}

/**
 * Checks whether the given rule params would match the template transaction.
 * Mirrors the backend CategorizationEngine for every condition type the quick
 * form produces (purpose/operation contains+regex, amount range, day of month).
 * Returns false when params carry no conditions.
 */
export function ruleMatchesTemplate(params: Record<string, string | number>, template: RuleTemplate): boolean {
  let hasCondition = false

  if (params.purpose_contains != null) {
    hasCondition = true
    const hay = normalizeCore(template.purpose ?? '')
    if (hay === '' || !hay.includes(normalizeCore(String(params.purpose_contains)))) return false
  }
  if (params.purpose_regex != null) {
    hasCondition = true
    const hay = template.purpose ?? ''
    if (hay === '' || !regexFind(String(params.purpose_regex), hay)) return false
  }
  if (params.operation_contains != null) {
    hasCondition = true
    const hay = normalizeCore(template.operation ?? '')
    if (hay === '' || !hay.includes(normalizeCore(String(params.operation_contains)))) return false
  }
  if (params.operation_regex != null) {
    hasCondition = true
    const hay = template.operation ?? ''
    if (hay === '' || !regexFind(String(params.operation_regex), hay)) return false
  }
  if (params.min != null || params.max != null) {
    hasCondition = true
    const amount = template.amount
    if (amount == null) return false
    if (params.min != null && amount < Number(params.min)) return false
    if (params.max != null && amount > Number(params.max)) return false
  }
  if (params.day_from != null || params.day_to != null) {
    hasCondition = true
    const day = dayOfMonth(template.date)
    if (day == null) return false
    if (params.day_from != null && day < Number(params.day_from)) return false
    if (params.day_to != null && day > Number(params.day_to)) return false
  }

  return hasCondition
}
