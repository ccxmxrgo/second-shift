package com.cxmxrgo.secondshift.employee;

import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Themed default-name pool for freshly bound employees (D-01, A2).
 *
 * <p>A "Second Shift" HR-flavored pool of office-worker names, matching the mod's existing
 * payroll/paperwork/position language (see {@code en_us.json}). Deliberately holds no persisted
 * counter state — a themed pool needs no world-scoped state, unlike an "Employee #N" counter.
 */
public final class EmployeeNames {

    private static final List<String> POOL = List.of(
            "Gary from Accounts",
            "Doreen the Intern",
            "Bartholomew Ledger",
            "Marge Overtime",
            "Cubicle Steve",
            "Prudence Payroll",
            "Nigel Middleman",
            "Beatrice Filecabinet",
            "Wendell Timesheet",
            "Agnes Watercooler",
            "Reginald Redtape",
            "Constance Clipboard",
            "Herbert Breakroom",
            "Mildred Memo",
            "Percival Paperwork",
            "Ophelia Overhead"
    );

    private EmployeeNames() {}

    /** Picks a uniformly random name from the pool. Never returns null or blank. */
    public static String pickRandom(RandomSource random) {
        return POOL.get(random.nextInt(POOL.size()));
    }
}
