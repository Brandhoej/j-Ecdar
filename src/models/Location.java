package models;

import logic.State;

import java.util.*;
import java.util.stream.Collectors;

public class Location {
    private final String name;

    private final List<Location> product;

    private final boolean isInitial;
    private final boolean isUrgent;
    private final boolean isUniversal;

    private final int x, y;

    private boolean isInconsistent;
    private Guard invariantGuard;
    private CDD inconsistentPartCdd;

    public Location(String name, Guard invariant, boolean isInitial, boolean isUrgent, boolean isUniversal, boolean isInconsistent, CDD inconsistentPart, int x, int y, List<Location> product) {
        this.name = name;
        this.invariantGuard = invariant;
        this.isInitial = isInitial;
        this.isUrgent = isUrgent;
        this.isUniversal = isUniversal;
        this.isInconsistent = isInconsistent || this.getName().equals("inc");
        this.inconsistentPartCdd = inconsistentPart;
        this.x = x;
        this.y = y;
        this.product = product;
    }

    public Location(String name, Guard invariant, boolean isInitial, boolean isUrgent, boolean isUniversal, boolean isInconsistent, CDD inconsistentPart, int x, int y) {
        this(
                name,
                invariant,
                isInitial,
                isUrgent,
                isUniversal,
                isInconsistent || name.equals("inc"),
                inconsistentPart,
                x,
                y,
                new ArrayList<>()
        );
    }

    public Location(String name, Guard invariant, boolean isInitial, boolean isUrgent, boolean isUniversal, boolean isInconsistent, int x, int y) {
        this(name, invariant, isInitial, isUrgent, isUniversal, isInconsistent, null, x, y);
    }

    public Location(String name, Guard invariant, boolean isInitial, boolean isUrgent, boolean isUniversal, boolean isInconsistent) {
        this(name, invariant, isInitial, isUrgent, isUniversal, isInconsistent, 0, 0);
    }

    public Location(Location copy, List<Clock> newClocks, List<Clock> oldClocks, List<BoolVar> newBVs, List<BoolVar> oldBVs) {
        this(copy.name, copy.invariantGuard.copy(newClocks, oldClocks, newBVs, oldBVs), copy.isInitial, copy.isUrgent, copy.isUniversal, copy.isInconsistent, copy.x, copy.y);
    }

    public Location(State state, List<Clock> clocks) {
        this(state.getLocation().getName(), state.getInvariants(clocks), state.getLocation().isInitial(), state.getLocation().isUrgent(), state.getLocation().isUniversal(), state.getLocation().isInconsistent(), state.getLocation().getX(), state.getLocation().getX());
    }

    public Location(Location location) {
        this(location.getName(), location.getInvariantGuard(), location.isInitial(), location.isUrgent(), location.isUniversal(), location.isInconsistent(), location.getX(), location.getY());
    }

    public static Location createProduct(CDD invariant, Location... locations) {
        return createProduct(Arrays.asList(locations), invariant);
    }

    public static Location createProduct(List<Location> product, CDD invariant) {
        return createProduct(product, invariant.getGuard());
    }

    public static Location createProduct(Guard invariant, Location... locations) {
        return createProduct(Arrays.asList(locations), invariant);
    }

    public static Location createProduct(List<Location> product, Guard invariant) {
        if (product.size() == 0) {
            throw new IllegalArgumentException("At least a single location is required");
        }

        int x = 0, y = 0;
        for (Location location : product) {
            x += location.x;
            y = location.y;
        }
        x /= product.size();
        y /= product.size();

        return new Location(
                product.stream().map(Location::getName).collect(Collectors.joining("")),
                invariant,
                product.stream().allMatch(location -> location.isInitial),
                product.stream().anyMatch(location -> location.isUrgent),
                product.stream().allMatch(location -> location.isUniversal),
                product.stream().anyMatch(location -> location.isInconsistent),
                null,
                x,
                y,
                product
        );
    }

    public static Location createProduct(List<Location> product) {
        CDD invariant = CDD.cddTrue();
        for (Location location : product) {
            invariant = invariant.conjunction(location.getInvariantCDD());
        }
        return createProduct(product, invariant);
    }

    public static Location createProduct(Location... product) {
        return createProduct(Arrays.asList(product));
    }

    public static Location createUniversalLocation(String name, int x, int y) {
        return new Location(name, new TrueGuard(), false, false, true, false, x, y);
    }

    public static Location createUniversalLocation(String name) {
        return createUniversalLocation(name, 0, 0);
    }

    public static Location createUniversalLocation() {
        return createUniversalLocation("univ-loc");
    }

    public static Location createInconsistentLocation(String name, int x, int y) {
        return new Location(name, CDD.cddZero().getGuard(), false, false, false, true, x, y);
    }

    public static Location createInconsistentLocation(String name) {
        return createInconsistentLocation(name, 0, 0);
    }

    public static Location createInconsistentLocation() {
        return createInconsistentLocation("inc-loc");
    }

    public static Location createInconsistentLocation(Location location, List<Clock> clocks) {
        if (!location.isInconsistent) {
            throw new IllegalArgumentException("Location must be inconsistent");
        }

        CDD invariant;
        if (location.getInconsistentPartCdd().isUnrestrained()) {
            invariant = CDD.cddFalse();
        } else {
            invariant = location.getInvariantCDD().minus(location.getInconsistentPartCdd());
        }

        return new Location(location.getName(), invariant.getGuard(), location.isInitial(), location.isUrgent(), location.isUniversal(), location.isInconsistent(), location.getX(), location.getY());
    }

    public String getName() {
        return name;
    }

    public List<Location> getProduct() {
        return product;
    }

    public boolean isProduct() {
        return !product.isEmpty();
    }

    public boolean isUrgent() {
        return isUrgent;
    }

    public boolean isInconsistent() {
        return isInconsistent;
    }

    public CDD getInvariantCDD() {
        return new CDD(invariantGuard);
    }

    public void setInvariantGuard(Guard invariantGuard) {
        this.invariantGuard = invariantGuard;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isUniversal() {
        return isUniversal;
    }

    public CDD getInconsistentPartCdd() {
        return inconsistentPartCdd;
    }

    public void setInconsistent(boolean inconsistent) {
        isInconsistent = inconsistent;
    }

    public void setInconsistentPartCdd(CDD inconsistentPartCdd) {
        this.inconsistentPartCdd = inconsistentPartCdd;
    }

    public Guard getInvariantGuard() {
        return invariantGuard;
    }

    public boolean isInitial() {
        return isInitial;
    }

    public int getMaxConstant(Clock clock) {
        return invariantGuard.getMaxConstant(clock);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Location)) {
            return false;
        }

        Location location = (Location) obj;

        return isInitial == location.isInitial && isUrgent == location.isUrgent && isUniversal == location.isUniversal && isInconsistent == location.isInconsistent && name.equals(location.name) && invariantGuard.equals(location.invariantGuard);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, isInitial, isUrgent, isUniversal, isInconsistent, invariantGuard, inconsistentPartCdd);
    }
}
