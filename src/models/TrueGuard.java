package models;

import java.util.Objects;

public class TrueGuard extends Guard{

    @Override
    int getMaxConstant(Clock clock) {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TrueGuard;
    }

    @Override
    public String toString() {
        return "true";
    }

    @Override
    public int hashCode() {
        return Objects.hash(true);
    }
}
