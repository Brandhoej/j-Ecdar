package logic;

import models.*;

import java.util.*;
import java.util.stream.Collectors;

public class LazyQuotient extends TransitionSystem {

    private final TransitionSystem specification;
    private final TransitionSystem component;

    private final Set<Channel> inputs;
    private final Set<Channel> outputs;

    private final Location universal_location;
    private final Location inconsistent_location;

    private final Channel newAction;
    private final Clock newClock;

    public LazyQuotient(TransitionSystem t, TransitionSystem s) {
        // Terminology: specification is "s" and "component" is "t"

        // Act_o^S ∩ Act_i^T = Ø
        if (!disjoint(s.getOutputs(), t.getInputs())) {
            Set<Channel> intersection = intersect(s.getOutputs(), t.getInputs());
            String violatingIntersection = toString(intersection);
            throw new IllegalArgumentException(
                    String.format("Specification output and component inputs are not disjoint, caused by %s", violatingIntersection)
            );
        }

        // Act_i = Act_i^T ∪ Act_o^S
        inputs = union(t.getInputs(), s.getOutputs());

        // Act_o = Act_o^T \ Act_o^S ∪ Act_i^S \ Act_i^T
        outputs = union(
                difference(t.getOutputs(), s.getOutputs()),
                difference(s.getInputs(), t.getInputs())
        );

        newAction = new Channel("i_new");
        inputs.add(newAction);

        newClock = new Clock("quo_new", "quo");
        clocks.add(newClock);

        universal_location = new Location("univ", new TrueGuard(), false, false, true, false);
        inconsistent_location = new Location("inc", new TrueGuard(), false, true, false, true);

        clocks.addAll(s.getClocks());
        clocks.addAll(t.getClocks());

        BVs.addAll(s.getBVs());
        BVs.addAll(t.getBVs());

        this.specification = s;
        this.component = t;
    }

    @Override
    public String getName() {
        return specification.getName() + "//" + component.getName();
    }

    @Override
    public Automaton getAutomaton() {
        return null;
    }

    @Override
    public Set<Channel> getInputs() {
        return inputs;
    }

    @Override
    public Set<Channel> getOutputs() {
        return outputs;
    }

    @Override
    public Set<Channel> getActions() {
        return union(getInputs(), getOutputs());
    }

    @Override
    public List<SimpleTransitionSystem> getSystems() {
        return null;
    }

    @Override
    protected Location getInitialLocation() {
        return null;
    }

    @Override
    public List<Transition> getNextTransitions(State state, Channel a, List<Clock> allClocks) {
        return null;
    }

    @Override
    protected List<Move> getNextMoves(Location location, Channel a) {
        /* Terminology: specification is "s", "component" is "t", and channel is "a"
         *   Transitions from "q" to "p" with "a" is "q -a-> p"
         *   "R" is the set of all reals and "R>=0" is reals where all is ">=0" */

        if (!in(a, getActions())) {
            throw new IllegalArgumentException(String.join("Action %s is not present in the action set", a.getName()));
        }

        List<Location> product = location.getProduct();
        Location qt = product.get(0); // Specification location
        Location qs = product.get(1); // Component location

        List<Move> t_moves = component.getNextMoves(qt, a);
        List<Move> s_moves = specification.getNextMoves(qs, a);

        List<Move> moves = new ArrayList<>();

        // Rule 1: "(q1^T q1^S) -a-> (q2^T q2^S)" if "a ∈ Act^S ∩ Act^T", "q1^T -a->^T q2^T" and "q1^S -a->^S q2^S"
        if (in(a, intersect(specification.getActions(), component.getActions()))) {
            moves.addAll(
                    merge(s_moves, t_moves)
            );
        }

        // Rule 2: "(q^T, q1^S) -a-> (q^T q2^S)" if "a ∈ Act^S \ Act^T", "q^T ∈ Q^T" and "q_1^S -a->^S q_2^S"
        if (in(a, difference(specification.getActions(), component.getActions()))) {
            Move loop = new Move(qs);
            moves.addAll(
                    merge(loop, t_moves)
            );
        }

        // Rule 3: "(q1^T q1^S) -d-> (q2^T q2^S)" if "d ∈ R>=0",    "q1^T -d->^T q2^T" and "q1^S -d->^S q2^S"
        // Rule 4: "(q^T q^S) -a-> u"             if "a ∈ Act_o^S", "q^T ∈ Q^T"        and "q^S -a/->^S"
        // Rule 5: "(q^T q^S) -d-> u"             if "d ∈ R>=0",    "q^T ∈ Q^T"        and "q^S -d/->^S"
        if (in(a, specification.getOutputs())) {
            // new Rule 3 (includes rule 4 by de-morgan)
            CDD guard_s = CDD.cddFalse();
            for (Move s_move : t_moves) {
                guard_s = guard_s.disjunction(s_move.getEnabledPart());
            }
            guard_s = guard_s.negation().removeNegative().reduce();

            moves.add(
                    new Move(location, universal_location, guard_s)
            );
        } else {
            // Rule 5
            moves.add(
                    new Move(location, universal_location)
            );
        }

        // Rule 6: "(q^T q^S) -a-> e" if "a ∈ Act_o^S ∩ Act_o^T", "q^T -a/->^T" and "q^S -a/->^S"
        if (in(a, intersect(specification.getOutputs(), component.getOutputs()))) {
            CDD guard_t = CDD.cddFalse();
            for (Move t_move : t_moves) {
                guard_t = guard_t.disjunction(t_move.getEnabledPart());
            }
            guard_t = guard_t.negation().removeNegative().reduce();

            for (Move s_move : s_moves) {
                CDD guard = s_move.getEnabledPart().conjunction(guard_t);
                ClockUpdate update = createNewClockReset();
                moves.add(
                        new Move(location, inconsistent_location, guard, update)
                );
            }
        }

        // Rule 7: "(q^T q^S) -a-> (q^T q^S)" if "a ∈ Act_o^S ∩ Act_o^T", "q^T -a/->^T" and "q^S -a/->^S"
        if (in(a, intersect(specification.getOutputs(), component.getOutputs()))) {
            CDD guard_s = qs.getInvariantCDD().negation();
            CDD guard_t = qt.getInvariantCDD();
            CDD combined = guard_s.conjunction(guard_t);
            moves.add(
                    new Move(location, inconsistent_location, combined, createNewClockReset())
            );
        }

        // Rule 8:"(q^T q^S) -a-> (q^T q^S)" if "a ∈ Act^T \ Act^S", "q^S ∈ Q^S" and "q1^T -a->^T q_2^T"
        if (in(a, difference(component.getActions(), specification.getActions()))) {
            Move loop = new Move(qt);
            moves.addAll(
                    merge(s_moves, loop)
            );
        }

        // Rule 9: "e -a-> e" if "a ∈ Act ∪ R>=0"
        if (location.isUniversal()) {
            moves.add(
                    new Move(location, universal_location)
            );
        }

        // Rule 10: "u -a-> u" if "a ∈ Act_i"
        if (location.isInconsistent() && in(a, inputs)) {
            // Reset clock t disallow progress of time
            moves.add(
                    new Move(location, inconsistent_location, createNewClockReset())
            );
        }

        return moves;
    }

    private List<Move> merge(Move s_move, List<Move> t_moves) {
        return merge(Collections.singletonList(s_move), t_moves);
    }

    private List<Move> merge(List<Move> s_moves, Move t_move) {
        return merge(s_moves, Collections.singletonList(t_move));
    }

    private List<Move> merge(List<Move> s_moves, List<Move> t_moves) {
        List<Move> moves = new ArrayList<>();
        for (Move s_move : s_moves) {
            for (Move t_move : t_moves) {

                Move newMove = merge(s_move, t_move);
                if (newMove.getGuardCDD().isNotFalse()) {
                    moves.add(newMove);
                }
            }
        }
        return moves;
    }

    private Move merge(Move s_move, Move t_move) {
        Location source = Location.createProduct(s_move.getSource(), t_move.getSource());
        Location target = Location.createProduct(CDD.cddTrue(), s_move.getTarget(), t_move.getTarget());
        List<Edge> edges = new ArrayList<>();
        edges.addAll(s_move.getEdges());
        edges.addAll(t_move.getEdges());
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

        return Location.createProduct(new TrueGuard(), specification, component);
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
