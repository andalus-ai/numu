# Sharia adviser sign-off sheet

Nine points where the code makes a choice the book does not settle. For each: what the code does today, and a blank for the adviser's ruling. The catalog (`ShariaRuleCatalog`, see `SHARIA-RULES.md`) is frozen only after every point is ruled and this sheet is filled in.

Adviser name: ____________________  Date: ____________  Signature: ____________________

| # | Point | What the code does today | Ruling (accept / change to ...) |
|---|-------|--------------------------|----------------------------------|
| 1 | Multi-akad | Blocked by default (the book prefers the view that it is haram). Setting: `FormationPolicy.blockMultiAkad`. | |
| 2 | Witness composition | Nishab by default: two men, or one man and two women, or four women. A plain count is available. | |
| 3 | Mufawadhah | Requires equal capital (Hanafi rule); the book defines it only as combining the other types. | |
| 4 | Wujuh and abdan loss sharing | Fail closed in the accounting module. | |
| 5 | Heir on a partner's death | The akad ends; the heir decides whether to take the deceased's place in a new akad after the books close. The inherited capital is not a single transfer in the ledger. | |
| 6 | Barred terms (rule 7) | Six clauses blocked: fixed profit amount, capital guarantee, manager salary, loss not by capital, capital provider control, profit exclusion. Full list wanted. | |
| 7 | Capital in goods | Allowed when valued at the time of the akad. | |
| 8 | Unanimity | All partners must consent to an akad change; day-to-day decisions need only the managing partners. Do capital-only partners have to consent to akad changes? | |
| 9 | Musaqah | Allowed only on land that already has trees. No ledger type exists for it; is one needed? | |

After signing: record the adviser's name and date in `ShariaRuleCatalog.ADVISER_SIGNOFF`, mark the ruled rules as confirmed, and set `FROZEN_FINGERPRINT` from `ShariaRuleCatalog.fingerprint()`.
