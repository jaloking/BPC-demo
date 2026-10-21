package Model;

/** Immutable three-customer subset-row inequality: sum floor(|r intersect S|/2) x_r <= 1. */
public final class Subset_Row_Cut {
    public final int first, second, third;

    public Subset_Row_Cut(int first, int second, int third) {
        if (first <= 0 || first >= second || second >= third)
            throw new IllegalArgumentException("Cut customers must be positive and strictly increasing.");
        this.first = first;
        this.second = second;
        this.third = third;
    }

    public boolean contains(int customer) {
        return customer == first || customer == second || customer == third;
    }

    public int coefficient(Route route) {
        int count = 0;
        for (int customer : route.nodes) if (contains(customer)) count++;
        return count / 2;
    }

    public String key() { return first + "_" + second + "_" + third; }
}
