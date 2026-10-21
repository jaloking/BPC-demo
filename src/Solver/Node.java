package Solver;

import Common.Data;
import Model.Route;
import Model.Route_Pool;
import java.util.Arrays;

/**
 * Stores all state required to define one branch-price-and-cut tree node.
 */
public class Node implements Comparable<Node> {

    public Data data;
    public int id;
    public int parent_id;
    public int depth;
    public double lower_bound;

    public int vehicle_lb;
    public int vehicle_ub;

    /** Arc restrictions inherited from branching decisions. */
    public boolean[][] forbidden_arcs;

    /** Routes inherited from the parent or generated at this node. */
    public Route_Pool route_pool;
    public java.util.ArrayList<Model.Subset_Row_Cut> cuts = new java.util.ArrayList<>();

    /** Returns a deep copy that can be used as a child node. */
    public Node get_copy() {
        Node copy = new Node();
        copy.data = data;
        copy.id = id;
        copy.parent_id = parent_id;
        copy.depth = depth;
        copy.lower_bound = lower_bound;
        copy.vehicle_lb = vehicle_lb;
        copy.vehicle_ub = vehicle_ub;
        copy.cuts = new java.util.ArrayList<>(cuts); // Cut objects are immutable.
        copy.forbidden_arcs = new boolean[data.node_num][];
        for (int i = 0; i < data.node_num; i++)
            copy.forbidden_arcs[i] = Arrays.copyOf(forbidden_arcs[i], data.node_num);
        copy.route_pool = new Route_Pool();
        for (Route route : route_pool.routes) copy.route_pool.add_route(route);
        return copy;
    }

    /** Forbids one directed arc. */
    public void forbid_arc(int from, int to) {
        forbidden_arcs[from][to] = true;
    }

    /** Enforces one directed arc by forbidding incompatible alternatives. */
    public void enforce_arc(int from, int to) {
        if (from == to) throw new IllegalArgumentException("不能强制自环。");
        // Customers have one predecessor and one successor. Depot has many.
        if (from != 0) {
            for (int j = 0; j < data.node_num; j++)
                if (j != to) forbid_arc(from, j);
        }
        if (to != 0) {
            for (int i = 0; i < data.node_num; i++)
                if (i != from) forbid_arc(i, to);
        }
    }

    /** Returns whether a route satisfies all arc restrictions of this node. */
    public boolean is_route_compatible(Route route) {
        for (int p = 1; p < route.nodes.size(); p++) {
            if (forbidden_arcs[route.nodes.get(p - 1)][route.nodes.get(p)]) return false;
        }
        return true;
    }

    /** Compares two open nodes for best-bound selection. */
    @Override
    public int compareTo(Node other) {
        int boundOrder = Double.compare(lower_bound, other.lower_bound);
        return boundOrder != 0 ? boundOrder : Integer.compare(id, other.id);
    }
}
