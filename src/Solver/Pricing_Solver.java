package Solver;

import Common.Data;
import Model.MP;
import Model.Route;
import Util.Helper;
import java.util.concurrent.CancellationException;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.HashSet;

/**
 * Solves the ESPPRC pricing problem by a label-setting algorithm.
 */
public class Pricing_Solver {

    public Data data;

    /** Labels stored at every graph node. */
    public ArrayList<ArrayList<Label>> labels_at_node;

    /** Negative reduced-cost routes found in the latest pricing call. */
    public ArrayList<Route> generated_routes;

    public double best_reduced_cost;
    /** True only after the complete label search finishes. */
    public boolean completed;
    /** Returning a batch early is safe; a later exhaustive call proves convergence. */
    public int max_routes_per_call = 100;
    public long deadline_nanos = Long.MAX_VALUE;

    /** Solves the pricing problem defined by the current RMP dual values. */
    public boolean solve(MP mp) {
        clear();
        data = mp.data;
        labels_at_node = new ArrayList<>();
        for (int i = 0; i < data.node_num; i++) labels_at_node.add(new ArrayList<>());
        Label root = new Label();
        root.end_node = 0;
        root.visited = new boolean[data.node_num];
        root.cut_parity = new boolean[mp.dual_cuts.length];
        root.service_start_time = data.ready_time[0];
        ArrayDeque<Label> pending = new ArrayDeque<>();
        pending.add(root);
        HashSet<String> keys = new HashSet<>();
        while (!pending.isEmpty()) {
            if (System.nanoTime() >= deadline_nanos) throw new CancellationException("Time limit reached.");
            Label current = pending.removeLast();
            if (current != root && !labels_at_node.get(current.end_node).contains(current)) continue;
            Label complete = current.extend(0, data, mp.node,
                    mp.dual_customers, mp.dual_vehicle, mp.phase_one, mp.dual_cuts);
            if (complete != null) {
                best_reduced_cost = Math.min(best_reduced_cost, complete.reduced_cost);
                if (complete.has_negative_reduced_cost()) {
                    Route route = complete.to_route(data);
                    int index = mp.route_pool.get_route_index(route);
                    if ((index < 0 || !mp.contains_route_column(index)) && keys.add(route.get_key()))
                        generated_routes.add(route);
                    if (generated_routes.size() >= max_routes_per_call) return true;
                }
            }
            for (int next = 1; next < data.node_num; next++) {
                Label candidate = current.extend(next, data, mp.node,
                        mp.dual_customers, mp.dual_vehicle, mp.phase_one, mp.dual_cuts);
                if (candidate != null && add_non_dominated_label(candidate)) pending.add(candidate);
            }
        }
        completed = true;
        // An existing column with a materially negative reduced cost indicates a bad dual solve.
        if (generated_routes.isEmpty() && best_reduced_cost < -10.0 * Helper.RC_EPS)
            throw new IllegalStateException("定价发现已有列具有负检验数，请检查 LP 对偶精度。");
        return !generated_routes.isEmpty();
    }

    /** Inserts a label and removes labels dominated by it. */
    private boolean add_non_dominated_label(Label candidate) {
        ArrayList<Label> labels = labels_at_node.get(candidate.end_node);
        for (Label existing : labels) if (existing.dominates(candidate)) return false;
        Iterator<Label> iterator = labels.iterator();
        while (iterator.hasNext()) if (candidate.dominates(iterator.next())) iterator.remove();
        labels.add(candidate);
        return true;
    }

    /** Releases labels and routes generated in the latest pricing call. */
    public void clear() {
        labels_at_node = new ArrayList<>();
        generated_routes = new ArrayList<>();
        best_reduced_cost = Double.POSITIVE_INFINITY;
        completed = false;
    }
}
