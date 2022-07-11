package models;

public class BoolVal {
    private BoolVar var;
    private boolean value;

    public BoolVal(BoolVar var, boolean value) {
        this.var = var;
        this.value = value;
    }

    public String getName() {
        return var.getOriginalName();
    }

    public BoolVar getVar() {
        return var;
    }

    public void setVar(BoolVar var) {
        this.var = var;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof BoolVal)) {
            return false;
        }

        BoolVal other = (BoolVal) obj;
        return other.getName().equals(getName())
                && other.value == value;

    }

    public String toString() {
        return getName() + " = " + getValue();
    }
}
