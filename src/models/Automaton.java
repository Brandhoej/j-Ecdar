package models;

import java.util.*;
import java.util.stream.Collectors;

public class Automaton {
    private String name;

    public List<Location> getLocations() {
        return locations;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public Set<Channel> getActions() {
        return actions;
    }

    public void setName(String name) {
        this.name = name;
    }

    private final List<Location> locations;
    private final List<BoolVar> BVs;
    private final List<Edge> edges;
    private final List<Clock> clocks;
    private Set<Channel> inputAct, outputAct, actions;
    private Location initLoc;

    public Automaton(String name, List<Location> locations, List<Edge> edges, List<Clock> clocks, List<BoolVar> BVs, boolean makeInpEnabled) {
        this.name = name;
        this.locations = locations;

        for (Location location : locations) {

            if (location.isInitial()) {
                initLoc = location;
                break;
            }
        }

        this.edges = edges;
        setActions(edges);
        this.clocks = clocks;
        this.BVs = BVs;


        if (makeInpEnabled) {
            CDD.init(CDD.maxSize, CDD.cs, CDD.stackSize);
            CDD.addClocks(clocks);
            CDD.addBddvar(BVs);
            addTargetInvariantToEdges();

            makeInputEnabled();

            CDD.done();
        }
    }

    public Automaton(String name, List<Location> locations, List<Edge> edges, List<Clock> clocks, List<BoolVar> BVs) {
        this(name, locations, edges, clocks, BVs, true);
    }

    // Copy constructor
    public Automaton(Automaton origin) {
        this.name = origin.name + "Copy";

        this.clocks = new ArrayList<>();
        for (Clock c : origin.clocks) {
            this.clocks.add(new Clock(c.getOriginalName() + "Copy", name));
        }
        this.BVs = new ArrayList<>();
        for (BoolVar c : origin.BVs) {
            this.BVs.add(new BoolVar(c.getOriginalName() + "Copy", name, c.getInitialValue()));
        }
        this.locations = new ArrayList<>();
        for (Location loc : origin.locations) {
            this.locations.add(new Location(loc, clocks, origin.clocks, BVs, origin.BVs));
            if (loc.isInitial()) this.initLoc = this.locations.get(this.locations.size() - 1);
        }
        this.edges = new ArrayList<>();
        for (Edge e : origin.edges) {
            int sourceIndex = origin.locations.indexOf(e.getSource());
            int targetIndex = origin.locations.indexOf(e.getTarget());
            this.edges.add(new Edge(e, this.clocks, this.BVs, locations.get(sourceIndex), locations.get(targetIndex), origin.clocks, origin.BVs));
        }

        this.inputAct = origin.inputAct;
        this.outputAct = origin.outputAct;
        this.actions = origin.actions;
    }

    public HashMap<Clock, Integer> getMaxBoundsForAllClocks() {
        HashMap<Clock, Integer> result = new HashMap<>();

        for (Clock clock : clocks) {
            for (Edge edge : edges) {
                result.compute(
                    clock,
                    (key, value) -> {
                        int clockMaxBound = edge.getMaxConstant(clock);
                        return Math.max(value == null ? clockMaxBound : value, clockMaxBound);
                    }
                );
            }

            for (Location location : locations) {
                result.compute(
                        clock,
                        (key, value) -> {
                            int clockMaxBound = location.getMaxConstant(clock);
                            return Math.max(value == null ? clockMaxBound : value, clockMaxBound);
                        }
                );
            }
            if (!result.containsKey(clock) || result.get(clock) == 0)
                result.put(clock, 1);
        }
        return result;
    }


    public String getName() {
        return name;
    }

    private List<Edge> getEdgesFromLocation(Location loc) {
        if (loc.isUniversal()) {
            List<Edge> result = new ArrayList<>();
            for (Channel action : actions) {
                result.add(new Edge(loc, loc, action, inputAct.contains(action), new TrueGuard(), new ArrayList<>()));
            }
            return result;
        }

        return edges.stream().filter(edge -> edge.getSource().equals(loc)).collect(Collectors.toList());
    }

    public List<Edge> getEdgesFromLocationAndSignal(Location loc, Channel signal) {
        List<Edge> resultEdges = getEdgesFromLocation(loc);

        return resultEdges.stream().filter(edge -> edge.getChannel().getName().equals(signal.getName())).collect(Collectors.toList());
    }

    private void setActions(List<Edge> edges) {
        inputAct = new HashSet<>();
        outputAct = new HashSet<>();
        actions = new HashSet<>();

        for (Edge edge : edges) {
            Channel action = edge.getChannel();

            actions.add(action);

            if (edge.isInput()) {
                inputAct.add(action);
            } else {
                outputAct.add(action);
            }
        }
    }

    public List<Clock> getClocks() {
        return clocks;
    }

    public Location getInitLoc() {
        return initLoc;
    }

    public Set<Channel> getInputAct() {
        return inputAct;
    }

    public Set<Channel> getOutputAct() {
        return outputAct;
    }

    public List<BoolVar> getBVs() {
        return BVs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Automaton)) return false;

        Automaton automaton = (Automaton) o;

        return name.equals(automaton.name) &&
                Arrays.equals(locations.toArray(), automaton.locations.toArray()) &&
                Arrays.equals(edges.toArray(), automaton.edges.toArray()) &&
                Arrays.equals(clocks.toArray(), automaton.clocks.toArray()) &&
                Arrays.equals(BVs.toArray(), automaton.BVs.toArray()) &&
                Arrays.equals(inputAct.toArray(), automaton.inputAct.toArray()) &&
                Arrays.equals(outputAct.toArray(), automaton.outputAct.toArray()) &&
                initLoc.equals(automaton.initLoc);
    }

    @Override
    public String toString() {
        return "Automaton{" +
                "name='" + name + '\'' +
                ", locations=" + Arrays.toString(locations.toArray()) +
                ", edges=" + Arrays.toString(edges.toArray()) +
                ", clocks=" + Arrays.toString(clocks.toArray()) +
                ", BVs=" + Arrays.toString(BVs.toArray()) +
                ", inputAct=" + inputAct +
                ", outputAct=" + outputAct +
                ", actions=" + actions +
                ", initLoc=" + initLoc +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, locations, edges, clocks, BVs, inputAct, outputAct, initLoc);
    }


    public void makeInputEnabled() {
        for (Location loc : getLocations()) {
            CDD sourceInvariantCDD = loc.getInvariantCDD();
            // loop through all inputs
            for (Channel input : getInputAct()) {

                // build CDD of zones from edges
                List<Edge> inputEdges = getEdgesFromLocationAndSignal(loc, input);
                CDD resCDD;
                CDD cddOfAllEdgesWithCurrentInput = CDD.cddFalse();
                if (!inputEdges.isEmpty()) {
                    for (Edge edge : inputEdges) {
                        CDD target = edge.getTarget().getInvariantCDD();
                        CDD preGuard1 = target.transitionBack(edge);
                        cddOfAllEdgesWithCurrentInput = cddOfAllEdgesWithCurrentInput.disjunction(preGuard1);
                    }
                    cddOfAllEdgesWithCurrentInput = cddOfAllEdgesWithCurrentInput.removeNegative().reduce();
                    // subtract the federation of zones from the original fed
                    resCDD = sourceInvariantCDD.minus(cddOfAllEdgesWithCurrentInput);
                } else {
                    resCDD = sourceInvariantCDD;
                }

                if (resCDD.isNotFalse()) {
                    Edge newEdge = new Edge(loc, loc, input, true, CDD.toGuardList(resCDD, getClocks()), new ArrayList<>());
                    getEdges().add(newEdge);
                }

            }
        }

    }

    public void addTargetInvariantToEdges() {
        for (Edge edge : getEdges()) {
            CDD targetCDD = edge.getTarget().getInvariantCDD();
            CDD past = targetCDD.transitionBack(edge);
            if (!past.equiv(CDD.cddTrue()))
                edge.setGuard(CDD.toGuardList(past.conjunction(edge.getGuardCDD()), getClocks()));
        }
    }

}
