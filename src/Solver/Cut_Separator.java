package Solver;

import Model.MP;
import Model.Subset_Row_Cut;
import Util.Helper;
import java.util.concurrent.CancellationException;
import java.util.ArrayList;
import java.util.HashSet;

/** Exhaustive separation of triples, adding only a bounded batch of the most violated cuts. */
public final class Cut_Separator {
    public ArrayList<Subset_Row_Cut> separate(MP mp, int limit, long deadline) {
        ArrayList<Subset_Row_Cut> candidates = new ArrayList<>();
        java.util.HashMap<String, Double> violations = new java.util.HashMap<>();
        HashSet<String> existing = new HashSet<>();
        for (Subset_Row_Cut cut : mp.node.cuts) existing.add(cut.key());
        if (limit <= 0) return candidates;
        for (int i = 1; i < mp.data.node_num; i++)
            for (int j = i + 1; j < mp.data.node_num; j++)
                for (int k = j + 1; k < mp.data.node_num; k++) {
                    if (System.nanoTime() >= deadline) throw new CancellationException("Time limit reached.");
                    Subset_Row_Cut cut = new Subset_Row_Cut(i, j, k);
                    if (existing.contains(cut.key())) continue;
                    double lhs = 0.0;
                    for (int r = 0; r < mp.active_route_indices.size(); r++)
                        lhs += cut.coefficient(mp.route_pool.get_route(mp.active_route_indices.get(r)))
                                * mp.active_route_values.get(r);
                    if (lhs > 1.0 + 10 * Helper.EPS) {
                        candidates.add(cut);
                        violations.put(cut.key(), lhs - 1.0);
                    }
                }
        candidates.sort((a, b) -> Double.compare(violations.get(b.key()), violations.get(a.key())));
        return new ArrayList<>(candidates.subList(0, Math.min(limit, candidates.size())));
    }
}
