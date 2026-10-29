# Sharia rules enforced by Numou syirkah

Generated from `ShariaRuleCatalog`. Do not edit by hand: change the catalog and regenerate. This is an implementation trace of one book, not a fatwa.

- Rules: 26
- Waiting for the Sharia adviser: 7
- Adviser sign-off: none yet
- Frozen: no

## Rule 1: Profit is split by the agreed nisbah on net profit, never on revenue

- Source: Hukum Muamalah Bab XIV, XXI
- Enforcement: BLOCKS
- Adviser: BOOK_STATED
- Finding codes: PROFIT_ON_REVENUE

## Rule 2: The nisbah totals 100% and names every partner

- Source: Hukum Muamalah Bab XIV, XXI
- Enforcement: BLOCKS
- Adviser: BOOK_STATED
- Finding codes: NISBAH_PARTNERS

## Rule 3: Profit exists only once capital is intact

- Source: Hukum Muamalah Bab XXI
- Enforcement: BEHAVIOUR
- Adviser: BOOK_STATED
- Tests: SyirkahTest
- Note: Capital is restored before any profit is split.

## Rule 4: Loss follows capital proportion

- Source: Hukum Muamalah Bab XV, XIV principle 10
- Enforcement: BLOCKS
- Adviser: PENDING_ADVISER
- Finding codes: LOSS_NOT_BY_CAPITAL, LOSS_SHARING_INVALID, LOSS_SHARING_SUM
- Note: Explicit loss shares are refused for the types that need cash capital. Abdan and wujuh loss models wait for the adviser.

## Rule 5: The akad needs ijab and qabul, in one session, not withdrawn

- Source: Hukum Muamalah Bab X (in'iqad conditions 6, 7)
- Enforcement: BLOCKS
- Adviser: BOOK_STATED
- Finding codes: IJAB_QABUL, PARTY_IJAB_QABUL_MISSING, SESSION_NOT_UNIFIED, IJAB_WITHDRAWN

## Rule 6: The akad is written and has named witnesses

- Source: Hukum Muamalah Bab XVIII
- Enforcement: BLOCKS
- Adviser: BOOK_STATED
- Finding codes: WITNESSES, WITNESS_NAME_BLANK, WITNESS_MISMATCH

## Rule 7: Barred terms make the akad invalid

- Source: Hukum Muamalah Bab X, XV
- Enforcement: BLOCKS
- Adviser: PENDING_ADVISER
- Finding codes: FORBIDDEN_*
- Note: Only six clauses are blocked: fixed profit amount, capital guarantee, manager salary, loss not by capital, capital provider control, profit exclusion. The adviser should supply the full list.

## Rule 8: Capital, work and roles fit the type of syirkah

- Source: Hukum Muamalah Bab XV
- Enforcement: BLOCKS
- Adviser: PENDING_ADVISER
- Finding codes: PARTNERS_MIN, PARTNER_DUPLICATE, CAPITAL_REQUIRED, CAPITAL_VALUATION, CAPITAL_UNKNOWN_PARTNER, NO_CONTRIBUTION, NO_CASH_CAPITAL, MUDHARABAH_ROLES, MUDHARABAH_MANAGER_CAPITAL, MUDHARABAH_MANAGER_WORK, MUDHARABAH_PROVIDER_CAPITAL, ABDAN_WORK, ROLE_MISMATCH, MUFAWADHAH_EQUAL_CAPITAL, MUFAWADHAH_EQUAL_WORK
- Note: Goods are allowed as capital when valued at the akad. The equal-capital check for mufawadhah is the Hanafi rule; the book defines mufawadhah as a combination of the other types.

## Rule 9: Changed terms need closed books, every partner's consent and a new akad

- Source: Hukum Muamalah Bab XIV principle 8, XXIV, XXV
- Enforcement: BLOCKS
- Adviser: BOOK_STATED
- Finding codes: NOT_ACTIVE, NEW_AKAD_VERSION, BOOKS_NOT_CLOSED, CONSENT_MISSING, DISSENT
- Note: Consents carry evidence; a dissent is kept and blocks the change.

## Rule 10: The mudharib bears loss only for negligence or breach

- Source: Hukum Muamalah Bab XV
- Enforcement: BEHAVIOUR
- Adviser: BOOK_STATED
- Tests: SyirkahTest
- Note: The evidence workflow for fault lives in the accounting module.

## Rule 11: Liquidation: sell, pay debts, then partners' claims

- Source: Hukum Muamalah Bab XXVI
- Enforcement: BEHAVIOUR
- Adviser: BOOK_STATED
- Tests: SyirkahTest
- Note: Assets are treated as already sold for cash; hak syuf'ah is checked per asset sale.

## Rule 12: Statements: balance sheet and profit and loss in the book's layout

- Source: Hukum Muamalah Bab XIX
- Enforcement: BEHAVIOUR
- Adviser: BOOK_STATED
- Tests: BookStatementsTest
- Note: The caller supplies assets and outside debts; the sheet shows any difference.

## Rule 13: Retained profit becomes partner capital

- Source: Hukum Muamalah Bab XXIII
- Enforcement: BEHAVIOUR
- Adviser: BOOK_STATED
- Tests: SyirkahTest

## Rule 14: Each party is fit to contract: of age, of sound mind, consenting, with authority over the capital

- Source: Hukum Muamalah Bab X (in'iqad condition 1), XVIII D.1
- Enforcement: RECORDED
- Adviser: BOOK_STATED
- Finding codes: PARTY_RECORD_DUPLICATE, PARTY_RECORD_UNKNOWN, PARTY_RECORD_MISSING, PARTY_DOB_MISSING, PARTY_DOB_INVALID, PARTY_BELOW_MINIMUM_AGE, PARTY_RESIDENCE_MISSING, PARTY_NOT_OF_SOUND_MIND, PARTY_NO_AUTHORITY_OVER_CAPITAL, PARTY_COERCED
- Note: Software cannot judge sound mind; it requires the determination to be recorded. No minimum age is invented unless the policy sets one.

## Rule 15: The subject of the akad is lawful

- Source: Hukum Muamalah Bab X (in'iqad conditions 2, 3)
- Enforcement: RECORDED
- Adviser: BOOK_STATED
- Finding codes: OBJECT_NOT_STATED, LAWFULNESS_REVIEW_MISSING, OBJECT_NOT_LAWFUL
- Note: Someone must record the review; a negative result blocks.

## Rule 16: Void (batil) and defective (fasad) akad are different outcomes

- Source: Hukum Muamalah Bab X
- Enforcement: BEHAVIOUR
- Adviser: BOOK_STATED
- Finding codes: AMBIGUOUS_TERMS
- Tests: AkadFormationCheckerTest
- Note: Every finding carries its class: batil, fasad, incomplete or unclassified. Where the book does not say, it is not called batil.

## Rule 17: Multi-akad is prohibited on the book's preferred view

- Source: Hukum Muamalah Bab XI
- Enforcement: BLOCKS
- Adviser: PENDING_ADVISER
- Finding codes: MULTI_AKAD
- Note: Scholars differ; the book prefers the view that it is haram. A policy setting turns it off.

## Rule 18: Witnesses: two men, or one man and two women, or four women

- Source: Hukum Muamalah Bab XVIII
- Enforcement: BLOCKS
- Adviser: PENDING_ADVISER
- Finding codes: WITNESS_NISHAB
- Note: A plain count of witnesses is available as a policy.

## Rule 19: The written akad carries its required parts

- Source: Hukum Muamalah Bab XVIII C.4, C.5
- Enforcement: BLOCKS
- Adviser: BOOK_STATED
- Finding codes: SECTION_MISSING_*, SIGNING_PLACE_MISSING, SIGNING_DATE_MISSING

## Rule 20: Decisions need the managing partners' agreement; firm dissent ends the syirkah

- Source: Hukum Muamalah Bab XIV principles 5 to 7
- Enforcement: BLOCKS
- Adviser: PENDING_ADVISER
- Finding codes: DISSENT
- Note: Enforced for akad changes only. Day-to-day management decisions are not a ledger concern. Whether capital-only partners must consent to akad changes needs the adviser.

## Rule 21: Staff are hired by the syirkah on a fixed wage, with clear work, period and effort

- Source: Hukum Muamalah Bab XIV principle 10, XVI D, XVII
- Enforcement: BLOCKS
- Adviser: BOOK_STATED
- Finding codes: STAFF_HIRED_BY_PARTNER, MANAGER_SALARY, WORK_TYPE_MISSING, WORK_PERIOD_MISSING, EFFORT_MISSING, WAGE_NOT_FIXED, WAGE_AMOUNT_MISSING, WAGE_CURRENCY, PAY_PERIOD_MISSING, JALI_REVIEW_MISSING
- Note: A pure check: staff contracts are not yet stored or exposed over REST.

## Rule 22: A trading syirkah's cost of goods sold is opening stock plus purchases minus closing stock

- Source: Hukum Muamalah Bab XXII
- Enforcement: BEHAVIOUR
- Adviser: BOOK_STATED
- Tests: TradingResultCalculatorTest
- Note: Periodic method only; lost goods sit inside the cost of goods sold.

## Rule 23: New capital: close the books, then a new akad

- Source: Hukum Muamalah Bab XIV principle 8, XXIV
- Enforcement: BLOCKS
- Adviser: BOOK_STATED
- Finding codes: PROFIT_DECISION_MISSING, CAPITAL_REDUCED, PARTNER_REMOVED, NO_NEW_CAPITAL

## Rule 24: Replacing a partner: every partner agrees, hak syuf'ah is offered, a new akad follows

- Source: Hukum Muamalah Bab XXV, XXVI
- Enforcement: BLOCKS
- Adviser: BOOK_STATED
- Finding codes: NO_LEAVING_PARTNER, NOT_A_PARTNER, LEAVING_PARTNER_REMAINS, SYUFAH_NOT_OFFERED, SYUFAH_EXERCISED

## Rule 25: A partner's death ends the akad; the heir decides whether to continue in a new akad

- Source: Hukum Muamalah Bab X (akad fasakh), XXIV
- Enforcement: BLOCKS
- Adviser: PENDING_ADVISER
- Finding codes: NO_DECEASED, DECEASED_REMAINS, SUCCESSION_UNDECIDED, SUCCESSION_FOR_LIVING, HEIR_NOT_IN_AKAD, HEIR_DECLINED_BUT_IN_AKAD, BOOKS_NOT_CLOSED_TO_DEATH
- Note: The book says death ends the akad. Continuing through the heir is the owner's rule and needs the adviser's reading. The heir's capital is not one transfer in the ledger.

## Rule 26: Muzara'ah and mukhabarah are haram; musaqah only on land that already has trees

- Source: Hukum Muamalah Bab XV section H
- Enforcement: BLOCKS
- Adviser: BOOK_STATED
- Finding codes: BARRED_AGRICULTURE_AKAD, MUSAQAH_NOT_PLANTED
- Note: Numou has no ledger type for agriculture; the name scan is a safeguard, not the lawfulness review.

