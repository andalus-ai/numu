# Islamic/Madrasah School Management System

Extension for Kemenag madrasah (RA, MI, MTs, MA). Attaches to a school owned by `school-management`
through `SchoolId`.

Aggregates: `MadrasahProfile` (NSM, level, PAI subject group, tahfidz flag), `DailyIbadahLog`
(five prayers, sunnah fast, tilawah pages). `HijriDates` gives Umm al-Qura based Hijri formatting.

Use cases: `RegisterMadrasahProfile` (madrasah levels only, one per school), `LogDailyIbadah` (same-day correction).
Verify against your data: NSM is validated as 12 digits; Hijri dates can differ by a day from Kemenag's sidang isbat.
