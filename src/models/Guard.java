package models;

public abstract class Guard {

    abstract int getMaxConstant(Clock clock);

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract String toString();

    @Override
    public abstract int hashCode();
}
