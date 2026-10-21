package Solver;

import Common.Data;
import Model.Route;
import Util.Helper;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Represents one partial path in the ESPPRC label-setting algorithm.
 */
public class Label {

    /** Last node of the partial path. */
    public int end_node;

    /** Customers already visited by the partial path. */
    public boolean[] visited;

    /** Accumulated vehicle load. */
    public int load;

    /** Service starting time at the last node. */
    public double service_start_time;

    /** Accumulated reduced cost. */
    public double reduced_cost;

    /** Predecessor label used to reconstruct a complete route. */
    public Label predecessor;
    /** Parity of visited customers in each active subset-row cut. */
    public boolean[] cut_parity = new boolean[0];

    /** Returns a deep copy of this label. */
    public Label get_copy() {
        Label copy = new Label();
        copy.end_node = end_node;
        copy.cut_parity = cut_parity.clone();
        copy.visited = visited == null ? null : visited.clone();
        copy.load = load;
        copy.service_start_time = service_start_time;
        copy.reduced_cost = reduced_cost;
        // Iterative copying avoids recursion on a long predecessor chain.
        Label source = predecessor;
        Label tail = copy;
        while (source != null) {
            Label previous = new Label();
            previous.end_node = source.end_node;
            previous.cut_parity = source.cut_parity.clone();
            previous.visited = source.visited == null ? null : source.visited.clone();
            previous.load = source.load;
            previous.service_start_time = source.service_start_time;
            previous.reduced_cost = source.reduced_cost;
            tail.predecessor = previous;
            tail = previous;
            source = source.predecessor;
        }
        return copy;
    }

    /** Extends this label to a customer or back to the depot. */
    public Label extend(int next, Data data, Node node,
                        double[] dual_customers, double dual_vehicle) {
        return extend(next, data, node, dual_customers, dual_vehicle, false);
    }

    /** Phase I prices feasibility columns with zero travel cost. */
    public Label extend(int next, Data data, Node node,
                        double[] dual_customers, double dual_vehicle, boolean phaseOne) {
        return extend(next, data, node, dual_customers, dual_vehicle, phaseOne, new double[0]);
    }

    public Label extend(int next, Data data, Node node,
                        double[] dual_customers, double dual_vehicle, boolean phaseOne, double[] dualCuts) {
        if (is_complete() || next == end_node || next < 0 || next >= data.node_num
                || node.forbidden_arcs[end_node][next]) return null;
        if (next == 0 && predecessor == null) return null;
        if (next != 0 && visited[next]) return null;
        int newLoad = load + data.demand[next];
        if (newLoad > data.capacity) return null;
        double time = Math.max(data.ready_time[next], service_start_time
                + data.service_time[end_node] + data.travel_time[end_node][next]);
        if (time > data.due_time[next] + Helper.EPS) return null;
        Label result = new Label();
        result.end_node = next;
        result.cut_parity = cut_parity.clone();
        if (result.cut_parity.length != dualCuts.length)
            throw new IllegalStateException("Cut pricing state does not match the master problem.");
        result.visited = visited.clone();
        if (next != 0) result.visited[next] = true;
        result.load = newLoad;
        result.service_start_time = time;
        result.reduced_cost = reduced_cost + (phaseOne ? 0.0 : data.cost[end_node][next]);
        if (next != 0) result.reduced_cost -= dual_customers[next];
        else result.reduced_cost -= dual_vehicle; // Exactly once per complete route.
        for (int c = 0; c < dualCuts.length; c++) {
            if (next != 0 && node.cuts.get(c).contains(next)) {
                if (result.cut_parity[c]) result.reduced_cost -= dualCuts[c];
                result.cut_parity[c] = !result.cut_parity[c];
            }
        }
        result.predecessor = this; // Labels are immutable after insertion.
        return result;
    }

    /** Returns whether this label dominates another label. */
    public boolean dominates(Label other) {
        // Equal parity guarantees identical future cut-cost increments for a common suffix.
        if (!java.util.Arrays.equals(cut_parity, other.cut_parity)) return false;
        if (end_node != other.end_node || is_complete() != other.is_complete()
                || load > other.load || service_start_time > other.service_start_time
                || reduced_cost > other.reduced_cost) return false;
        for (int i = 1; i < visited.length; i++)
            if (visited[i] && !other.visited[i]) return false;
        return true;
    }

    /** Returns whether the label represents a complete depot-to-depot route. */
    public boolean is_complete() {
        return end_node == 0 && predecessor != null;
    }

    /** Reconstructs a route by following predecessor labels. */
    public Route to_route(Data data) {
        if (!is_complete()) throw new IllegalStateException("不能从未完成的标签还原路径。");
        Route route = new Route();
        route.nodes = new ArrayList<>();
        for (Label label = this; label != null; label = label.predecessor)
            route.nodes.add(label.end_node);
        Collections.reverse(route.nodes);
        route.set_route_info(data);
        return route;
    }

    /** Returns whether this complete label has negative reduced cost. */
    public boolean has_negative_reduced_cost() {
        return is_complete() && reduced_cost < -Helper.RC_EPS;
    }
}
