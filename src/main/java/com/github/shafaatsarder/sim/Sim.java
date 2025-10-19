package com.github.shafaatsarder.sim; // package for your classes

import java.io.BufferedReader;        // to read text line-by-line
import java.io.IOException;           // for input exception handling
import java.io.InputStreamReader;     // to wrap System.in
import java.util.Random;              // random-number generator

public class Sim {

    // ===== Global parameters =====
    public static double simulationTime;     // current simulated time
    public static double reportInterval;     // how often we print a stats snapshot

    public static double profit = 0.025;     // profit per litre of fuel sold
    public static double pumpCost = 20.0;    // daily cost per pump (flat cost)

    public static double litresNeededMin = 10.0;    // min litres a car needs
    public static double litresNeededRange = 50.0;  // additional random range (so 10..60)

    public static double serviceTimeBase = 150.0;   // base seconds per service
    public static double serviceTimePerLitre = 0.5; // extra seconds per litre pumped
    public static double serviceTimeSpread = 30.0;  // std dev for normal noise

    public static double balkA = 40.0;  // balk formula constant A
    public static double balkB = 25.0;  // balk formula constant B
    public static double balkC = 3.0;   // balk formula constant C

    public static double meanInterarrivalTime = 50.0; // mean time between car arrivals

    // Independent random streams for different stochastic components
    public static Random arrivalStream;  // randomness for arrivals
    public static Random litreStream;    // randomness for litres needed
    public static Random balkingStream;  // randomness for balk decision
    public static Random serviceStream;  // randomness for service-time noise

    // Major model objects (singletons for this run)
    public static EventList eventList;   // future events sorted by time
    public static CarQueue carQueue;     // FIFO queue of waiting cars
    public static PumpStand pumpStand;   // pool of pumps
    public static Statistics stats;      // collects & prints statistics

    // ===== Main =====
    public static void main(String[] args) throws IOException {
        // Reader to get parameters from stdin (one value per line)
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        // 1) read general run parameters
        reportInterval = Double.parseDouble(in.readLine().trim()); // snapshot period
        double endingTime = Double.parseDouble(in.readLine().trim()); // when to stop
        int numPumps = Integer.parseInt(in.readLine().trim());        // pump count

        // Show configuration header
        System.out.print("This simulation run uses " + numPumps + " pumps");
        System.out.println(" and the following random number seeds:");

        // 2) read seeds and build independent Random streams
        int seed = Integer.parseInt(in.readLine().trim()); // seed for arrivals
        arrivalStream = new Random(seed);
        System.out.print(seed + " ");

        seed = Integer.parseInt(in.readLine().trim());     // seed for litres
        litreStream = new Random(seed);
        System.out.print(seed + " ");

        seed = Integer.parseInt(in.readLine().trim());     // seed for balking
        balkingStream = new Random(seed);
        System.out.print(seed + " ");

        seed = Integer.parseInt(in.readLine().trim());     // seed for service time
        serviceStream = new Random(seed);
        System.out.println(seed); // end line of seeds banner

        // 3) create model components
        eventList = new EventList();          // empty future-event list
        carQueue = new CarQueue();            // empty waiting line
        pumpStand = new PumpStand(numPumps);  // stand with N pumps
        stats = new Statistics();             // stats printer/collector

        // 4) schedule initial events
        EndOfSimulation endEvent = new EndOfSimulation(endingTime); // stop marker
        eventList.insert(endEvent);                                 // put in FEL

        if (reportInterval <= endingTime)                           // only if useful
            eventList.insert(new Report(reportInterval));           // first snapshot

        eventList.insert(new Arrival(0.0));     // first arrival at time 0

        // 5) main simulation loop: pop next event, jump time, execute, repeat
        while (true) {
            Event currentEvent = eventList.takeNextEvent(); // earliest future event
            simulationTime = currentEvent.getTime();        // advance clock to it
            currentEvent.makeItHappen();                    // execute event logic
            if (currentEvent instanceof EndOfSimulation)    // if that was the stop
                break;                                      // exit the loop
        }
    }

    // ===== Statistics =====
    static class Statistics {
        private int totalArrivals = 0;        // number of cars that showed up
        private int customersServed = 0;      // number actually served
        private int balkingCustomers = 0;     // number who left without service
        private double totalLitresSold = 0.0;     // litres sold
        private double totalLitresMissed = 0.0;   // litres we could have sold
        private double totalWaitingTime = 0.0;    // sum of waits for served cars
        private double totalServiceTime = 0.0;    // sum of pump busy time

        public Statistics() {
            printHeaders(); // print table headers at the start
        }

        public void accumBalk(double litres) {
            balkingCustomers++;       // count a lost customer
            totalLitresMissed += litres; // track lost litres -> lost profit
        }

        public void accumSale(double litres) {
            customersServed++;        // count a sale
            totalLitresSold += litres; // and add litres sold
        }

        public void accumServiceTime(double t) {
            totalServiceTime += t;    // add busy time to utilization
        }

        public void accumWaitingTime(double t) {
            totalWaitingTime += t;    // add wait to average wait calc
        }

        public void countArrival() {
            totalArrivals++;          // bump total arrivals
        }

        // Format a double with width/precision (left pads with spaces)
        private static String fmtDbl(double num, int width, int prec) {
            double scale = 1.0;                   // 10^prec for rounding
            for (int i = 0; i < prec; i++) scale *= 10.0;
            String result = "" + (int) (num * scale + 0.5); // round to int string

            if (prec > 0) {                       // reinsert decimal point
                while (result.length() < prec + 1) result = "0" + result;
                int insertPos = result.length() - prec;
                result = result.substring(0, insertPos) + "." + result.substring(insertPos);
            }
            while (result.length() < width) result = " " + result; // left-pad spaces
            return result;
        }

        // Format an int with fixed width (left pads with spaces)
        private static String fmtInt(int num, int width) {
            String result = "" + num;
            while (result.length() < width) result = " " + result;
            return result;
        }

        // Print the table heading once
        private static void printHeaders() {
            System.out.println("Current  Total  NoQueue  Car->Car  Average  Number  Average    Pump    Total     Lost");
            System.out.println("  Time    Cars  Fraction   Time     Litres  Balked    Wait     Usage   Profit   Profit");
            for (int i = 0; i < 90; i++) System.out.print("-"); // underline
            System.out.println();
        }

        // Print a single snapshot line with all current aggregates
        public void snapshot() {
            System.out.print(fmtDbl(Sim.simulationTime, 8, 0)); // time column
            System.out.print(fmtInt(totalArrivals, 7));          // arrivals so far

            // fraction of time the queue has been empty so far
            double noQueueFrac = (Sim.simulationTime > 0)
                    ? (Sim.carQueue.getEmptyTime() / Sim.simulationTime)
                    : 0.0;
            System.out.print(fmtDbl(noQueueFrac, 9, 3));

            // average interarrival and litres per arrival
            if (totalArrivals > 0) {
                double carToCar = Sim.simulationTime / totalArrivals; // mean time/arrival
                double avgLitres = (totalLitresSold + totalLitresMissed) / totalArrivals;
                System.out.print(fmtDbl(carToCar, 9, 3));
                System.out.print(fmtDbl(avgLitres, 8, 3));
            } else {
                System.out.print("   Unknown   Unknown");
            }

            System.out.print(fmtInt(balkingCustomers, 8)); // how many balked

            // average waiting time per served customer
            if (customersServed > 0) {
                double avgWait = totalWaitingTime / customersServed;
                System.out.print(fmtDbl(avgWait, 9, 3));
            } else {
                System.out.print("   Unknown");
            }

            // pump utilization fraction = busy / (pumps * time)
            double pumpUsage = (Sim.pumpStand.getNumberOfPumps() > 0 && Sim.simulationTime > 0)
                    ? totalServiceTime / (Sim.pumpStand.getNumberOfPumps() * Sim.simulationTime)
                    : 0.0;
            System.out.print(fmtDbl(pumpUsage, 8, 3));

            // profits: earned minus pump fixed cost, and lost profit from balks
            double totalProfit = totalLitresSold * Sim.profit - Sim.pumpCost * Sim.pumpStand.getNumberOfPumps();
            double lostProfit = totalLitresMissed * Sim.profit;
            System.out.print(fmtDbl(totalProfit, 10, 2));
            System.out.print(fmtDbl(lostProfit, 9, 2));
            System.out.println();
        }
    }

    // ===== Car =====
    static class Car {
        private double arrivalTime;         // when this car arrived to the system
        private final double litresNeeded;  // litres requested by this car

        public Car() {
            // sample litres uniformly in [min, min+range]
            this.litresNeeded = Sim.litresNeededMin
                    + Sim.litreStream.nextDouble() * Sim.litresNeededRange;
        }

        public double getArrivalTime() { return arrivalTime; }     // read arrival time
        public double getLitresNeeded() { return litresNeeded; }   // read litres
        public void setArrivalTime(double time) { this.arrivalTime = time; } // set arrival
    }

    // ===== CarQueue =====
    static class CarQueue {
        // Node in the singly linked list (each node holds a Car + link to next)
        private class QueueItem {
            public Car data;       // the car in this node
            public QueueItem next; // next node (null if this is the tail)
        }

        private QueueItem first;              // head of the queue
        private QueueItem last;               // tail of the queue
        private int size;                     // current number of waiting cars
        private double totalEmptyQueueTime;   // accumulated time with size==0
        private boolean empty = true;         // are we currently empty?
        private double lastEmptyTime = 0.0;   // time when we last became empty

        // Total empty time so far (includes the ongoing empty spell, if any)
        public double getEmptyTime() {
            if (empty)
                return totalEmptyQueueTime + (Sim.simulationTime - lastEmptyTime);
            return totalEmptyQueueTime;
        }

        public int getQueueSize() { return size; } // current queue length

        // Add a car to the end (tail) of the queue
        public void insert(Car car) {
            QueueItem item = new QueueItem(); // create a new node
            item.data = car;                   // store the car
            item.next = null;                  // will be the tail

            if (last == null) {                // queue was empty
                first = item;                  // head and tail are this node
                last = item;
                size = 1;                      // size becomes 1
                if (empty) {                   // end an empty spell; record it
                    totalEmptyQueueTime += (Sim.simulationTime - lastEmptyTime);
                    empty = false;             // mark as non-empty now
                }
            } else {                            // queue already had items
                last.next = item;               // link old tail -> new tail
                last = item;                    // update tail pointer
                size++;                         // increase length
            }
        }

        // Remove and return the front car (head of the queue)
        public Car takeFirstCar() {
            if (size <= 0 || first == null) {            // safety check
                System.out.println("Error! car queue unexpectedly empty");
                return null;
            }
            Car c = first.data;                           // grab the car
            size--;                                       // shrink size
            first = first.next;                           // move head forward
            if (first == null) {                          // queue became empty
                last = null;                              // clear tail too
                empty = true;                             // mark empty state
                lastEmptyTime = Sim.simulationTime;       // start timing new empty spell
            }
            return c;                                     // return the car to caller
        }
    }

    // ===== Pump =====
    static class Pump {
        private Car carInService; // car currently being served at this pump

        public Car getCarInService() { return carInService; } // read the car

        // Setter to clear/set the car currently attached to the pump
        public void setCarInService(Car c) { this.carInService = c; }

        // Compute the duration of service for the current car (with noise)
        private double serviceTime() {
            if (carInService == null) {                          // sanity check
                System.out.println("Error! no car in service when expected");
                return -1.0;
            }
            // base + per-litre + Gaussian noise
            double t = Sim.serviceTimeBase
                    + Sim.serviceTimePerLitre * carInService.getLitresNeeded()
                    + Sim.serviceTimeSpread * Sim.serviceStream.nextGaussian();
            if (t < 0.1) t = 0.1; // clamp tiny negatives from Gaussian tails
            return t;
        }

        // Start serving a car at this pump and schedule its departure
        public void startService(Car car) {
            carInService = car;                             // attach car to pump
            final double pumpTime = serviceTime();          // draw service time
            Sim.stats.accumWaitingTime(Sim.simulationTime   // record wait = now - arrival
                    - carInService.getArrivalTime());
            Sim.stats.accumServiceTime(pumpTime);           // add busy time
            Departure dep = new Departure(Sim.simulationTime + pumpTime); // make departure
            dep.setPump(this);                              // tell it which pump
            Sim.eventList.insert(dep);                      // schedule the departure
        }
    }

    // ===== PumpStand =====
    static class PumpStand {
        private final Pump[] pumps; // array acting like a stack of free pumps
        private final int numPumps; // total pumps
        private int topPump;        // index of top free pump (>=0 means available)

        public PumpStand(int n) {
            if (n < 1) throw new IllegalArgumentException("Need at least 1 pump");
            pumps = new Pump[n];                // allocate array
            numPumps = n;                       // store count
            topPump = n - 1;                    // all pumps start free (stack full)
            for (int i = 0; i < n; i++) pumps[i] = new Pump(); // create each pump
        }

        public boolean aPumpIsAvailable() { return topPump >= 0; } // any free pump?
        public int getNumberOfPumps() { return numPumps; }         // total pumps

        // Return a pump to the pool of free pumps
        public void releasePump(Pump p) {
            if (topPump >= numPumps - 1) {                // already all free?
                System.out.println("Error! attempt to release a free pump?");
                return;
            }
            pumps[++topPump] = p;                          // push back to stack
        }

        // Take one available pump from the pool
        public Pump takeAvailablePump() {
            if (topPump < 0) {                             // none left
                System.out.println("Error! no pump available when needed");
                return null;
            }
            return pumps[topPump--];                       // pop from stack
        }
    }

    // ===== Event =====
    static abstract class Event {
        private double time;              // when this event occurs
        public Event(double time) {       // constructor sets the time
            this.time = time;
        }
        public double getTime() {         // read event time
            return time;
        }
        public void setTime(double t) {   // change (reschedule) event time
            this.time = t;
        }
        public abstract void makeItHappen(); // polymorphic event logic
    }

    // ===== EventList =====
    static class EventList {
        // Node in linked list of future events (sorted by time)
        private class ListItem {
            public Event data;    // the event
            public ListItem next; // next event in time order
        }

        private ListItem first;   // head of the list (earliest event)

        // Insert event e into the list keeping time order
        public void insert(Event e) {
            ListItem item = new ListItem(); // create node
            item.data = e;                  // store event
            double t = e.getTime();         // time to compare on

            if (first == null || t < first.data.getTime()) { // goes to head?
                item.next = first;          // link to old head
                first = item;               // make this the new head
            } else {
                ListItem behind = first;    // scan pointers
                ListItem ahead = first.next;
                while (ahead != null && ahead.data.getTime() <= t) {
                    behind = ahead;         // step forward
                    ahead = ahead.next;
                }
                behind.next = item;         // splice item between behind and ahead
                item.next = ahead;
            }
        }

        // Remove and return the earliest event
        public Event takeNextEvent() {
            if (first == null) {                        // nothing left -> error
                System.out.println("Error! ran out of events");
                return null;
            }
            Event e = first.data;                       // grab head event
            first = first.next;                         // drop head
            return e;                                   // return it
        }
    }

    // ===== Arrival =====
    static class Arrival extends Event {
        public Arrival(double time) { super(time); } // set arrival time

        // Decide if the arriving car balks given litres and queue length
        private boolean doesCarBalk(double litres, int queueLength) {
            if (queueLength == 0) return false; // never balk if no line
            double pNotBalk = (Sim.balkA + litres) / (Sim.balkB * (Sim.balkC + queueLength));
            return Sim.balkingStream.nextDouble() > pNotBalk; // true means it balks
        }

        // Draw exponential interarrival time using inverse CDF
        private double interarrivalTime() {
            double u = Sim.arrivalStream.nextDouble(); // uniform(0,1)
            return -Sim.meanInterarrivalTime * Math.log(u); // exponential(mean)
        }

        @Override
        public void makeItHappen() {
            Car arrivingCar = new Car();            // create new car
            Sim.stats.countArrival();               // bump arrivals stat
            double litres = arrivingCar.getLitresNeeded(); // its demand

            if (doesCarBalk(litres, Sim.carQueue.getQueueSize())) {
                Sim.stats.accumBalk(litres);       // record lost opportunity
            } else {
                arrivingCar.setArrivalTime(Sim.simulationTime); // remember when
                if (Sim.pumpStand.aPumpIsAvailable()) {         // free pump?
                    Pump p = Sim.pumpStand.takeAvailablePump(); // take it
                    if (p != null) p.startService(arrivingCar); // and start service
                } else {
                    Sim.carQueue.insert(arrivingCar);           // otherwise wait in line
                }
            }

            // Schedule the next arrival using the same event object (reschedule-and-reinsert)
            setTime(Sim.simulationTime + interarrivalTime());
            Sim.eventList.insert(this);
        }
    }

    // ===== Departure =====
    static class Departure extends Event {
        private Pump pump;                          // the pump this departure is for
        public Departure(double time) { super(time); } // set departure time
        public void setPump(Pump p) { this.pump = p; } // link to pump

        @Override
        public void makeItHappen() {
            if (pump == null || pump.getCarInService() == null) return; // guard
            Car departingCar = pump.getCarInService();  // finished car
            Sim.stats.accumSale(departingCar.getLitresNeeded()); // record litres sold
            if (Sim.carQueue.getQueueSize() > 0) {      // anyone waiting?
                pump.startService(Sim.carQueue.takeFirstCar()); // start next immediately
            } else {
                pump.setCarInService(null);             // detach car
                Sim.pumpStand.releasePump(pump);        // return pump to pool
            }
        }
    }

    // ===== Report =====
    static class Report extends Event {
        public Report(double time) { super(time); } // first report time

        @Override
        public void makeItHappen() {
            Sim.stats.snapshot();                         // print one line
            setTime(Sim.simulationTime + Sim.reportInterval); // schedule next report
            Sim.eventList.insert(this);                   // reinsert into FEL
        }
    }

    // ===== End of Simulation =====
    static class EndOfSimulation extends Event {
        public EndOfSimulation(double time) { super(time); } // stop time

        @Override
        public void makeItHappen() {
            Sim.stats.snapshot(); // print final snapshot; loop will break in main
        }
    }
}