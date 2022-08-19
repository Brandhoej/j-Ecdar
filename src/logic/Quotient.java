package logic;

import models.*;

import java.util.*;
import java.util.stream.Collectors;

public class Quotient extends TransitionSystem {



    /*
    Commands for compiling the DBM


    g++ -shared -o DBM.dll -I"c:\Program Files\Java\jdk1.8.0_172\include" -I"C:\Program Files\Java\jdk1.8.0_172\include\win32" -fpermissive lib_DBMLib.cpp ../dbm/libs/libdbm.a ../dbm/libs/libbase.a ../dbm/libs/libdebug.a ../dbm/libs/libhash.a ../dbm/libs/libio.a
    C:\PROGRA~1\Java\jdk1.8.0_172\bin\javac.exe DBMLib.java -h .

    261
    */

    private final TransitionSystem t, s;
    private final Set<Channel> inputs, outputs;
    private final Channel newChan;
    private Clock newClock;
    private boolean printComments = false;
    Location univ;
    Location inc;

    private List<Update> allResets;

    private final HashMap<Clock, Integer> maxBounds = new HashMap<>();
    private final HashSet<State> passed = new HashSet<>();
    private final Queue<State> worklist = new ArrayDeque<>();
    private Automaton resultant = null;

    public Quotient(TransitionSystem t, TransitionSystem s) {
        this.t = t;
        this.s = s;

        //clocks should contain the clocks of ts1, ts2 and a new clock
        newClock = new Clock("quo_new", "quo"); //TODO: get ownerName in a better way
        clocks.add(newClock);
        clocks.addAll(t.getClocks());
        clocks.addAll(s.getClocks());
        BVs.addAll(t.getBVs());
        BVs.addAll(s.getBVs());

        // Act_i = Act_i^T ∪ Act_o^S
        inputs = union(t.getInputs(), s.getOutputs());
        newChan = new Channel("i_new");
        inputs.add(newChan);

        // Act_o = Act_o^T \ Act_o^S ∪ Act_i^S \ Act_i^T
        outputs = union(
                difference(t.getOutputs(), s.getOutputs()),
                difference(s.getInputs(), t.getInputs())
        );

        maxBounds.putAll(t.getMaxBounds());
        maxBounds.putAll(s.getMaxBounds());

        allResets = new ArrayList<>();
        for (Clock clock : clocks.getItems()) {
            allResets.add(
                    new ClockUpdate(clock, 0)
            );
        }
    }

    @Override
    public Automaton getAutomaton() {
        boolean initialisedCdd = CDD.tryInit(clocks.getItems(), BVs.getItems());

        String name = getName();

        Set<Edge> edges = new HashSet<>();
        Set<Location> locations = new HashSet<>();
        Map<String, Location> locationMap = new HashMap<>();

        Location initial = getInitialLocation();
        locations.add(getInitialLocation());
        locationMap.put(initial.getName(), initial);

        Set<Channel> channels = new HashSet<>();
        channels.addAll(getOutputs());
        channels.addAll(getInputs());

        worklist.add(
                getInitialState()
        );

        while (!worklist.isEmpty()) {
            State state = worklist.remove();
            passed.add(state);

            for (Channel channel : channels) {
                List<Transition> transitions = getNextTransitions(state, channel, clocks.getItems());

                for (Transition transition : transitions) {
                    /* Get the state following the transition and then extrapolate. If we have not
                     *   already visited the location, this is equivalent to simulating the arrival
                     *   at that location following this transition with the current "channel". */
                    State targetState = transition.getTarget();
                    if (!havePassed(targetState) && !isWaitingFor(targetState)) {
                        targetState.extrapolateMaxBounds(maxBounds, getClocks());
                        worklist.add(targetState);
                    }

                    /* If we don't already have the "targetState" location added
                     *   To the set of locations for the conjunction then add it. */
                    String targetName = targetState.getLocation().getName();
                    locationMap.computeIfAbsent(
                            targetName, key -> {
                                Location newLocation = new Location(targetState, clocks.getItems());
                                locations.add(newLocation);
                                return newLocation;
                            }
                    );

                    // Create and add the edge connecting the conjoined locations
                    String sourceName = transition.getSource().getLocation().getName();

                    assert locationMap.containsKey(sourceName);
                    assert locationMap.containsKey(targetName);

                    Edge edge = createEdgeFromTransition(
                            transition,
                            locationMap.get(sourceName),
                            locationMap.get(targetName),
                            channel
                    );
                    if (!containsEdge(edges, edge)) {
                        edges.add(edge);
                    }
                }
            }
        }

        List<Location> updatedLocations = updateLocations(
                locations, getClocks(), getClocks(), getBVs(), getBVs()
        );
        List<Edge> edgesWithNewClocks = updateEdges(edges, clocks.getItems(), clocks.getItems(), BVs.getItems(), BVs.getItems());
        Automaton resAut = new Automaton(name, updatedLocations, edgesWithNewClocks, clocks.getItems(), BVs.getItems(), false);

        if (initialisedCdd) {
            CDD.done();
        }

        return resAut;
    }

    private boolean havePassed(State element) {
        for (State state : passed) {
            if (element.getLocation().getName().equals(state.getLocation().getName()) &&
                    element.getInvariant().isSubset(state.getInvariant())) {
                return true;
            }
        }
        return false;
    }

    private boolean isWaitingFor(State element) {
        for (State state : worklist) {
            if (element.getLocation().getName().equals(state.getLocation().getName()) &&
                    element.getInvariant().isSubset(state.getInvariant())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEdge(Set<Edge> set, Edge edge) {
        return set.stream().anyMatch(other -> other.equals(edge) &&
                other.getGuardCDD().equals(edge.getGuardCDD())
        );
    }

    private Edge createEdgeFromTransition(Transition transition, Location source, Location target, Channel channel) {
        Guard guard = transition.getGuards(getClocks());
        List<Update> updates = transition.getUpdates();
        boolean isInput = getInputs().contains(channel);
        return new Edge(source, target, channel, isInput, guard, updates);
    }

    public Location getInitialLocation() {
        // the invariant of locations consisting of locations from each transition system should be true
        // which means the location has no invariants
        return getInitialLocation(new TransitionSystem[]{t, s}, true);
    }

    public Set<Channel> getInputs() {
        return inputs;
    }

    public Set<Channel> getOutputs() {
        return outputs;
    }

    public List<SimpleTransitionSystem> getSystems() {
        // no idea what this is for
        List<SimpleTransitionSystem> result = new ArrayList<>();
        result.addAll(t.getSystems());
        result.addAll(s.getSystems());
        return result;
    }

    @Override
    public String getName() {
        return t.getName() + "//" + s.getName();
    }

    public List<Transition> getNextTransitions(State currentState, Channel channel, List<Clock> allClocks) {
        // get possible transitions from current state, for a given channel
        Location location = currentState.getLocation();

        List<Move> moves = getNextMoves(location, channel);

        // assert(!result.isEmpty());
        return createNewTransitions(currentState, moves, allClocks);
    }

    public List<Move> getNextMoves(Location location, Channel a) {
        univ = new Location("univ-loc", new TrueGuard(), false, false, true, false, 0, 0);
        inc = new Location("inc-loc", new ClockGuard(newClock, 0, Relation.LESS_EQUAL), false, false, false, true, 0, 0);

        List<Move> moves = new ArrayList<>();
        if (location.isProduct()) {
            List<Location> product = location.getProduct();
            assert product.size() == 2;

            Location qt = product.get(0); // Specification location
            Location qs = product.get(1); // Component location

            List<Move> t_moves = t.getNextMoves(qt, a);
            List<Move> s_moves = s.getNextMoves(qs, a);

            // Rule 1 (Cartesian product): "(q1^T q1^S) -a-> (q2^T q2^S)" if "a ∈ Act^S ∩ Act^T", "q1^T -a->^T q2^T" and "q1^S -a->^S q2^S"
            // "a ∈ Act^S ∩ Act^T"
            if (in(a, intersect(s.getActions(), t.getActions()))) {
                /* Merge handles "(q1^T q1^S) -a-> (q2^T q2^S)": The cartesian product of T and S moves
                 *  "q1^T -a->^T q2^T": is t_moves
                 *  "q1^S -a->^S q2^S": is s_moves */
                moves.addAll(
                        merge(t_moves, s_moves)
                );
            }

            // Rule 2 (Channels in comp not in spec): "(q^T, q1^S) -a-> (q^T q2^S)" if "a ∈ Act^S \ Act^T", "q^T ∈ Q^T" and "q_1^S -a->^S q_2^S"
            // "a ∈ Act^S \ Act^T"
            if (in(a, difference(s.getActions(), t.getActions()))) {
                /* Merge handles "(q^T, q1^S) -a-> (q^T q2^S)"
                 *   "q^T ∈ Q^T": is qt as it is the location of specification
                 *   "q_1^S -a->^S q_2^S": is s-moves */
                moves.addAll(
                        merge(qt, s_moves)
                );
            }

            // Rule 3: "(q1^T q1^S) -d-> (q2^T q2^S)" if "d ∈ R>=0",    "q1^T -d->^T q2^T" and "q1^S -d->^S q2^S"
            // Rule 4: "(q^T q^S) -a-> u"             if "a ∈ Act_o^S", "q^T ∈ Q^T"        and "q^S -a/->^S"
            // "a ∈ Act_o^S" (Rule 4)
            if (in(a, s.getOutputs())) {
                // Rule 3 (includes rule 4 by de-morgan)
                /* De-morgan explanation for rule 3 and 4:
                 *   By negating rule 3 we get */
                CDD guard_s = CDD.cddFalse();
                for (Move s_move : s_moves) {
                    guard_s = guard_s.disjunction(s_move.getEnabledPart());
                }
                guard_s = guard_s.negation().removeNegative().reduce();

                // "q^S -a/->^S": Negated component location (qs) invariant
                CDD inv_neg_inv_loc_s = qs.getInvariantCDD().negation().removeNegative().reduce();

                // Combining rule 3 and 4
                CDD combined = guard_s.disjunction(inv_neg_inv_loc_s);

                moves.add(
                        new Move(location, univ, combined, allResets)
                );
            } else {
                // Rule 5: "(q^T q^S) -d-> u"             if "d ∈ R>=0",    "q^T ∈ Q^T"        and "q^S -d/->^S"
                // "q^S -a/->^S": Negated component location (qs) invariant
                CDD inv_neg_inv_loc_s = qs.getInvariantCDD().negation().removeNegative().reduce();

                moves.add(
                        new Move(location, univ, inv_neg_inv_loc_s, allResets)
                );
            }

            // Rule 6 (edge to inconsistent for common outputs blocked in spec): "(q^T q^S) -a-> e" if "a ∈ Act_o^S ∩ Act_o^T", "q^T -a/->^T" and "q^S -a/->^S"
            // "a ∈ Act_o^S ∩ Act_o^T"
            if (in(a, intersect(s.getOutputs(), t.getOutputs()))) {
                // "q^T -a/->^T": Negate the guard from qt and all its targets
                // First: Find "q^T -a->^T" which is the disjunction of all guards from qt to its possible targets with a
                CDD guard_t = CDD.cddFalse();
                for (Move t_move : t_moves) {
                    guard_t = guard_t.disjunction(t_move.getEnabledPart());
                }
                // Second: Negate "q^T -a->^T" to ""q^T -a/->^T"
                guard_t = guard_t.negation().removeNegative().reduce();


                // "(q^T q^S) -a-> e"
                for (Move s_move : s_moves) {
                    CDD guard_s = s_move.getEnabledPart();
                    // Combine the guards of qt and qs targets
                    CDD guard = guard_s.conjunction(guard_t);
                    moves.add(
                            new Move(location, inc, guard, createNewClockReset())
                    );
                }
            }

            // Rule 7 (Edge for negated spec invariant): "(q^T q^S) -a-> (q^T q^S)" if "a ∈ Act_o^S ∩ Act_o^T", "q^T -a/->^T" and "q^S -a/->^S"
            // "a ∈ Act_o^S ∩ Act_o^T"
            if (in(a, intersect(s.getOutputs(), t.getOutputs()))) {
                // "q^T -a/->^T": Negate specification invariant
                CDD guard_t = qt.getInvariantCDD().negation().removeNegative().reduce();
                // "q^S -a/->^S": Negate component invariant
                CDD guard_s = qs.getInvariantCDD().negation().removeNegative().reduce();
                CDD combined = guard_s.conjunction(guard_t).removeNegative().reduce();
                moves.add(
                        new Move(location, inc, combined, createNewClockReset())
                );
            }

            // Rule 8 (independent action in spec):"(q^T q^S) -a-> (q^T q^S)" if "a ∈ Act^T \ Act^S", "q^S ∈ Q^S" and "q1^T -a->^T q_2^T"
            // "a ∈ Act^T \ Act^S"
            if (in(a, difference(t.getActions(), s.getActions()))) {
                /* merge handle (q^T q^S) -a-> (q^T q^S)
                 *   "q^S ∈ Q^S": Component location
                 *   "q1^T -a->^T q_2^T": specification move */
                moves.addAll(
                        merge(t_moves, qs)
                );
            }
        }
        // Rule 10: "u -a-> u" if "a ∈ Act_i"
        else if (location.isInconsistent() && in(a, inputs)) {
            moves.add(
                    new Move(location, inc, createNewClockReset())
            );
        }
        // Rule 9: "e -a-> e" if "a ∈ Act ∪ R>=0"
        else if (location.isUniversal()) {
            Move move = new Move(location, univ);
            move.setUpdates(allResets);
            moves.add(move);
        }

        return moves;
    }


    private List<Move> merge(Move t_move, List<Move> s_moves) {
        return merge(Collections.singletonList(t_move), s_moves);
    }

    private List<Move> merge(List<Move> t_moves, Move s_move) {
        return merge(t_moves, Collections.singletonList(s_move));
    }

    private List<Move> merge(List<Move> t_moves, List<Move> s_moves) {
        List<Move> moves = new ArrayList<>();
        for (Move t_move : t_moves) {
            for (Move s_move : s_moves) {

                Move newMove = merge(t_move, s_move);
                if (newMove.getGuardCDD().isNotFalse()) {
                    moves.add(newMove);
                }
            }
        }
        return moves;
    }

    private Move merge(Move t_move, Move s_move) {
        Location source = merge(t_move.getSource(), s_move.getSource());
        Location target = merge(new TrueGuard(), t_move.getTarget(), s_move.getTarget());
        List<Edge> edges = new ArrayList<>();
        edges.addAll(t_move.getEdges());
        edges.addAll(s_move.getEdges());
        Move move = new Move(source, target, edges);
        move.conjunctCDD(move.getEnabledPart());
        return move;
    }

    private List<Move> merge(List<Move> t_moves, Location s_location) {
        List<Move> moves = new ArrayList<>();
        for (Move t_move : t_moves) {
            Move newMove = merge(t_move, s_location);
            if (newMove.getGuardCDD().isNotFalse()) {
                moves.add(newMove);
            }
        }
        return moves;
    }

    private Move merge(Move t_move, Location s_location) {
        Location source = merge(t_move.getSource(), s_location);
        Location target = merge(new TrueGuard(), t_move.getTarget(), s_location);
        List<Edge> edges = new ArrayList<>(t_move.getEdges());
        Move move = new Move(source, target, edges);
        move.conjunctCDD(move.getEnabledPart());
        return move;
    }

    private List<Move> merge(Location t_location, List<Move> s_moves) {
        List<Move> moves = new ArrayList<>();
        for (Move s_move : s_moves) {
            Move newMove = merge(t_location, s_move);
            if (newMove.getGuardCDD().isNotFalse()) {
                moves.add(newMove);
            }
        }
        return moves;
    }

    private Move merge(Location t_location, Move s_move) {
        Location source = merge(t_location, s_move.getSource());
        Location target = merge(new TrueGuard(), t_location, s_move.getTarget());
        List<Edge> edges = new ArrayList<>(s_move.getEdges());
        Move move = new Move(source, target, edges);
        move.conjunctCDD(move.getEnabledPart());
        return move;
    }

    private Location merge(Location specification, Location component) {
        if (specification == component &&
                (specification.isUniversal() && component.isInconsistent())
        ) {
            return new Location(specification);
        }

        return Location.createProduct(specification, component);
    }

    private Location merge(Guard guard, Location specification, Location component) {
        if (specification == component &&
                (specification.isUniversal() && component.isInconsistent())
        ) {
            return new Location(specification);
        }

        return Location.createProduct(guard, specification, component);
    }

    private ClockUpdate createNewClockReset() {
        return new ClockUpdate(newClock, 0);
    }

    private String toString(Set<Channel> set) {
        return "{" + set
                .stream()
                .map(Channel::getName)
                .collect(Collectors.joining(", ")) + "}";
    }

    private boolean in(Channel element, Set<Channel> set) {
        return set.contains(element);
    }

    private boolean disjoint(Set<Channel> set1, Set<Channel> set2) {
        return empty(intersect(set1, set2));
    }

    private boolean empty(Set<Channel> set) {
        return set.isEmpty();
    }

    private Set<Channel> intersect(Set<Channel> set1, Set<Channel> set2) {
        Set<Channel> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        return intersection;
    }

    private Set<Channel> difference(Set<Channel> set1, Set<Channel> set2) {
        Set<Channel> difference = new HashSet<>(set1);
        difference.removeAll(set2);
        return difference;
    }

    private Set<Channel> union(Set<Channel> set1, Set<Channel> set2) {
        Set<Channel> union = new HashSet<>(set1);
        union.addAll(set2);
        return union;
    }
}
