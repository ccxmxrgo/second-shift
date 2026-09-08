package com.cxmxrgo.secondshift.employee;

/**
 * POL-07: HR flavour — per-tier job titles surfaced in the employee's own display name (e.g.
 * "Intern Aldric" at bind, "Associate Aldric" after its first promotion). Purely cosmetic — never
 * read back by any game logic, so a future tweak to this list can never break a save.
 */
public final class JobTitles {

    private static final String[] TITLES = {"Intern", "Associate", "Senior", "Lead", "Principal"};

    private JobTitles() {}

    /** {@code tier} is 1-indexed (matches {@code EmployeeData#tier()}/vanilla's own villager
     * level numbering); clamped into range so a tier beyond the array (shouldn't happen — vanilla
     * tops out at 5) never throws. */
    public static String forTier(int tier) {
        int index = Math.max(1, Math.min(tier, TITLES.length)) - 1;
        return TITLES[index];
    }
}
