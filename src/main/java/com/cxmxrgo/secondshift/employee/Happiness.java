package com.cxmxrgo.secondshift.employee;

import com.cxmxrgo.secondshift.config.ModConfig;
import net.minecraft.network.chat.Component;

/**
 * HAPP-03: the discrete happiness state derived from the 0-100 meter tracked on the
 * {@code secondshift:happiness} attachment. Band edges are configurable (POL-06:
 * {@code ModConfig#HAPPINESS_UNHAPPY_MAX}/{@code HAPPINESS_OK_MAX}) — default thirds (0-33
 * Unhappy, 34-66 OK, 67-100 Happy).
 */
public enum Happiness {
    UNHAPPY(-1, "message.secondshift.happiness.unhappy"),
    OK(0, "message.secondshift.happiness.ok"),
    HAPPY(1, "message.secondshift.happiness.happy");

    /** HAPP-04: the flat emerald-price adjustment (via {@code MerchantOffer#setSpecialPriceDiff})
     * this tier applies to every one of the employee's current offers. Negative = cheaper than
     * vanilla, positive = more expensive — see {@code EmployeeEvents}' happiness recompute. */
    private final int priceAdjustment;
    private final String translationKey;

    Happiness(int priceAdjustment, String translationKey) {
        this.priceAdjustment = priceAdjustment;
        this.translationKey = translationKey;
    }

    public static Happiness fromMeter(int meter) {
        if (meter <= ModConfig.HAPPINESS_UNHAPPY_MAX.get()) {
            return UNHAPPY;
        }
        if (meter <= ModConfig.HAPPINESS_OK_MAX.get()) {
            return OK;
        }
        return HAPPY;
    }

    public int priceAdjustment() {
        return priceAdjustment;
    }

    public Component label() {
        return Component.translatable(translationKey);
    }
}
