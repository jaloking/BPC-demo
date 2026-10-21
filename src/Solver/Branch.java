package Solver;

import Common.Data;
import Model.MP;
import Model.Route;
import Util.Helper;

/**
 * Selects a branching decision and creates two child nodes.
 */
public class Branch {

    public Data data;
    public MP mp;

    public Node left_node;
    public Node right_node;

    public double vehicle_value;

    public int from_node;
    public int to_node;
    public double arc_flow;

    /** Selects a branching rule and creates two child nodes. */
    public boolean branch(MP mp) {
        this.mp = mp;
        data = mp.data;
        left_node = null;
        right_node = null;
        return branch_vehicle() || branch_arc();
    }

    /** Branches on the fractional number of selected routes. */
    private boolean branch_vehicle() {
        vehicle_value = 0.0;
        for (double value : mp.active_route_values) vehicle_value += value;
        if (Helper.is_integral(vehicle_value)) return false;
        initialize_child_nodes();
        left_node.vehicle_ub = Math.min(left_node.vehicle_ub, Helper.floor(vehicle_value));
        right_node.vehicle_lb = Math.max(right_node.vehicle_lb, Helper.ceiling(vehicle_value));
        return true;
    }

    /** Branches on a fractional aggregated arc flow. */
    private boolean branch_arc() {
        double[][] flows = new double[data.node_num][data.node_num];
        for (int k = 0; k < mp.active_route_indices.size(); k++) {
            Route route = mp.route_pool.get_route(mp.active_route_indices.get(k));
            double value = mp.active_route_values.get(k);
            for (int p = 1; p < route.nodes.size(); p++)
                flows[route.nodes.get(p - 1)][route.nodes.get(p)] += value;
        }
        double best = Double.POSITIVE_INFINITY;
        from_node = -1;
        to_node = -1;
        for (int i = 0; i < data.node_num; i++) {
            for (int j = 0; j < data.node_num; j++) {
                if (i == j || Helper.is_integral(flows[i][j])) continue;
                double score = Helper.distance_to_half(flows[i][j]);
                if (score < best) {
                    best = score;
                    from_node = i;
                    to_node = j;
                    arc_flow = flows[i][j];
                }
            }
        }
        if (from_node < 0) return false;
        initialize_child_nodes();
        left_node.forbid_arc(from_node, to_node);
        right_node.enforce_arc(from_node, to_node);
        return true;
    }

    /** Copies the parent state into two child nodes. */
    private void initialize_child_nodes() {
        left_node = mp.node.get_copy();
        right_node = mp.node.get_copy();
        left_node.parent_id = right_node.parent_id = mp.node.id;
        left_node.depth = right_node.depth = mp.node.depth + 1;
        left_node.lower_bound = right_node.lower_bound = mp.objective_value;
    }
}
