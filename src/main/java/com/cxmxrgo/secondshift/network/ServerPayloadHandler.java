package com.cxmxrgo.secondshift.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Transitional stub (Plan 04-03 Task 1) — implemented fully in Task 2. Exists now only so
 * {@code SecondShift.registerPayloads}'s method reference compiles.
 */
public final class ServerPayloadHandler {

    private ServerPayloadHandler() {}

    public static void handleBindEmployee(BindEmployeePayload payload, IPayloadContext context) {
        // Implemented in Task 2.
    }
}
