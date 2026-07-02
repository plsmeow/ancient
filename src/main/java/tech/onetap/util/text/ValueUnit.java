package tech.onetap.util.text;

public final class ValueUnit {

    public enum Kind {
        COUNTABLE,
        ABBREVIATION
    }

    private final Kind kind;

    private final String one;
    private final String few;
    private final String many;

    private final String raw;

    private ValueUnit(Kind kind, String one, String few, String many, String raw) {
        this.kind = kind;
        this.one = one;
        this.few = few;
        this.many = many;
        this.raw = raw;
    }

    public static ValueUnit countable(String one, String few, String many) {
        return new ValueUnit(Kind.COUNTABLE, one, few, many, null);
    }

    public static ValueUnit abbreviation(String raw) {
        return new ValueUnit(Kind.ABBREVIATION, null, null, null, raw);
    }

    public String format(double value) {
        if (kind == Kind.ABBREVIATION) {
            return raw;
        }

        double abs = Math.abs(value);
        double fraction = abs - Math.floor(abs);

        if (fraction > 0) {
            return fraction < 0.5 ? few : many;
        }

        int v = (int) abs % 100;
        int v1 = v % 10;

        if (v >= 11 && v <= 19) return many;
        if (v1 == 1) return one;
        if (v1 >= 2 && v1 <= 4) return few;
        return many;
    }
}