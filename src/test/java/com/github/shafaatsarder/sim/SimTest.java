package com.github.shafaatsarder.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PumpStandTest {

    @Test
    void PumpBehaviour() {
        Sim.pumpStand = new Sim.PumpStand(2);

        assertTrue(Sim.pumpStand.aPumpIsAvailable());
        assertEquals(2, Sim.pumpStand.getNumberOfPumps());

        Sim.Pump p1 = Sim.pumpStand.takeAvailablePump();
        assertNotNull(p1);

        assertTrue(Sim.pumpStand.aPumpIsAvailable());

        Sim.Pump p2 = Sim.pumpStand.takeAvailablePump();
        assertNotNull(p2);

        assertFalse(Sim.pumpStand.aPumpIsAvailable());

        Sim.pumpStand.releasePump(p2);
        assertTrue(Sim.pumpStand.aPumpIsAvailable());

        Sim.pumpStand.releasePump(p1);
        assertTrue(Sim.pumpStand.aPumpIsAvailable());
    }
}