package betterquesting.api.enums;

public enum EnumLogic {

    AND, // All true
    NAND, // Any false
    OR, // Any true
    NOR, // All false
    XOR, // Only one true
    XNOR; // Only one false

    public boolean isTrivial() {
        switch (this) {
            case AND:
            case OR:
                return true;
            default:
                return false;
        }
    }

    public boolean isUnlockable(int inputs, int total) {
        switch (this) {
            case AND:
            case OR:
                return true;
            case NAND:
            case XNOR:
                return inputs < total;
            case NOR:
                return inputs == 0;
            case XOR:
                return inputs <= 1;
            default:
                return false;
        }
    }

    public boolean getResult(int inputs, int total) {
        switch (this) {
            case AND:
                return inputs >= total;
            case NAND:
                return inputs < total;
            case NOR:
                return inputs == 0;
            case OR:
                return inputs > 0;
            case XNOR:
                return inputs == total - 1;
            case XOR:
                return inputs == 1;
            default:
                return false;
        }
    }
}
