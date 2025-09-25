-- Eksempel på output:
--  måned,    arena,          tiltakspenger, total
--  2024 09,  2199 (100.0%),  0 (0.0%),      2199
--  2024 10,  3921 (100.0%),  0 (0.0%),      3921
--  2024 11,  3377 (100.0%),  1 (0.0%),      3378
--  2024 12,  2519 (100.0%),  0 (0.0%),      2519
--  2025 01,  4247 (100.0%),  0 (0.0%),      4247
--  2025 02,  3693 (100.0%),  0 (0.0%),      3693
--  2025 03,  3777 (100.0%),  1 (0.0%),      3778
--  2025 04,  2943 (99.9%),   2 (0.1%),      2945
--  2025 05,  2934 (100.0%),  0 (0.0%),      2934
--  2025 06,  2413 (100.0%),  0 (0.0%),      2413
--  2025 07,  1387 (100.0%),  0 (0.0%),      1387
--  2025 08,  2753 (99.8%),   5 (0.2%),      2758
--  2025 09,  2463 (96.3%),   95 (3.7%),     2558
--  Total,    38626 (99.7%),  104 (0.3%),    38730
SELECT year_month as Måned, arena, tiltakspenger, total
FROM (SELECT TO_CHAR(opprettet, 'YYYY MM')                                               AS year_month,
             COUNT(*) FILTER (WHERE eier = 'arena') || ' (' ||
             ROUND(COUNT(*) FILTER (WHERE eier = 'arena') * 100.0 / COUNT(*), 1) || '%)' AS Arena,
             COUNT(*) FILTER (WHERE eier = 'tp') || ' (' ||
             ROUND(COUNT(*) FILTER (WHERE eier = 'tp') * 100.0 / COUNT(*), 1) || '%)'    AS Tiltakspenger,
             COUNT(*)                                                                    AS total,
             0                                                                           AS sort_order
      FROM søknad
      GROUP BY TO_CHAR(opprettet, 'YYYY MM')

      UNION ALL

      SELECT 'Total'                                                                     AS year_month,
             COUNT(*) FILTER (WHERE eier = 'arena') || ' (' ||
             ROUND(COUNT(*) FILTER (WHERE eier = 'arena') * 100.0 / COUNT(*), 1) || '%)' AS Arena,
             COUNT(*) FILTER (WHERE eier = 'tp') || ' (' ||
             ROUND(COUNT(*) FILTER (WHERE eier = 'tp') * 100.0 / COUNT(*), 1) || '%)'    AS Tiltakspenger,
             COUNT(*)                                                                    AS total,
             1                                                                           AS sort_order
      FROM søknad) AS results
ORDER BY sort_order, year_month;
