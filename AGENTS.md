# Project conventions (for AI agents)

## Git workflow

- **Never commit without explicit user approval.** Prepare changes in the working
  tree (staged or unstaged), report what was changed, and wait for the user to
  verify correctness before committing.
- **The user commits and pushes themselves.** Do not run `git commit` or `git push`.

## Stage gates

- Proceed to the next implementation stage only after explicit user consent
  (e.g. "приступай к этапу N"). After finishing a stage, pause and wait for the "go".

## Security

- Secrets only via env / docker secrets; never commit real passwords or PII.
- Test PDFs must be sanitized (no real personal data).

## Other

- Не используй термин MVP при работе над новыми частями и функциями проекта. У нас нет задачи проверять гипотезы и делать минимальные жизнеспособные версии — мы сразу работаем в парадигме «делаем правильно, надолго и архитектурно устойчиво». Наши компоненты готовы к масштабированию, учитывают вопросы безопасности, переиспользования в других участках системы. Во время проектирования мы стараемся не обрезать функционал, а, наоборот, думать на несколько шагов вперёд. В случае чего, я самостоятельно обозначу границы ближайшей реализуемой версии.
- При разработке используй DRY-подход. Прежде чем разрабатывать новую функцию или придумывать новый стиль — обязательно убедись в том, что чего-то похожего уже нет в проекте. Do your very best to make code DRY.