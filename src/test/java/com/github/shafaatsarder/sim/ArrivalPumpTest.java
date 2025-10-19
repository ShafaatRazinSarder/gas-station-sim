package com.github.shafaatsarder.sim;


import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;

class ArrivalPumpTest {

    @Test
    void arrivalUsesFreePumpAndReschedulesNextArrival() {
        // Fresh world
        Sim.eventList = new Sim.EventList();
        Sim.carQueue = new Sim.CarQueue();
        Sim.pumpStand = new Sim.PumpStand(1);
        Sim.stats = new Sim.Statistics();
        Sim.simulationTime = 0.0;

    
        Sim.meanInterarrivalTime = 50.0;
        Sim.arrivalStream = new Random(5);
        Sim.litreStream = new Random(2);
        Sim.balkingStream = new Random(3);
        Sim.serviceStream = new Random(1);

        // Runnign arrival at t=0
        Sim.Arrival arr = new Sim.Arrival(0.0);
        arr.makeItHappen();

        // One car olny
        Sim.Pump p = Sim.pumpStand.takeAvailablePump(); // should be null because in use
        assertNull(p, "Pump should be busy after the arrival");
    }
    }
