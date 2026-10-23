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

    /** Visited-customer bit mask: customer i uses bit i - 1 (up to 32 customers). */
    public int visited;

    /** Accumulated vehicle load. */
    public int load;

    /** Service starting time at the last node. */
    public double service_start_time;

    /** Accumulated reduced cost. */
    public double reduced_cost;

    /** Predecessor label used to reconstruct a complete route. */
    public Label predecessor;
    /** Bit c is the visit parity of active subset-row cut c (up to 32 cuts). */
    public int cut_parity;

    /** Returns a deep copy of this label. */
    public Label get_copy() {
        Label copy = new Label();
        copy.end_node = end_node;
        copy.cut_parity = cut_parity;
        copy.visited = visited;
        copy.load = load;
        copy.service_start_time = service_start_time;
        copy.reduced_cost = reduced_cost;
        // Iterative copying avoids recursion on a long predecessor chain.
        Label source = predecessor;
        Label tail = copy;
        while (source != null) {
            Label previous = new Label();
            previous.end_node = source.end_node;
            previous.cut_parity = source.cut_parity;
            previous.visited = source.visited;
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
        int bit = next == 0 ? 0 : 1 << (next - 1);
        if ((visited & bit) != 0) return null;
        int newLoad = load + data.demand[next];
        if (newLoad > data.capacity) return null;
        double time = Math.max(data.ready_time[next], service_start_time
                + data.service_time[end_node] + data.travel_time[end_node][next]);
        if (time > data.due_time[next] + Helper.EPS) return null;
        Label result = new Label();
        result.end_node = next;
        result.cut_parity = cut_parity;
        result.visited = visited | bit;
        result.load = newLoad;
        result.service_start_time = time;
        result.reduced_cost = reduced_cost + (phaseOne ? 0.0 : data.cost[end_node][next]);
        if (next != 0) result.reduced_cost -= dual_customers[next];
        else result.reduced_cost -= dual_vehicle; // Exactly once per complete route.
        for (int c = 0; c < dualCuts.length; c++) {
            if (next != 0 && node.cuts.get(c).contains(next)) {
                int cutBit = 1 << c;
                if ((result.cut_parity & cutBit) != 0) result.reduced_cost -= dualCuts[c];
                result.cut_parity ^= cutBit;
            }
        }
        result.predecessor = this; // Labels are immutable after insertion.
        return result;
    }

    /** Returns whether this label dominates another label. */
    public boolean dominates(Label other) {
        // Equal parity guarantees identical future cut-cost increments for a common suffix.
        if (cut_parity != other.cut_parity) return false;
        if (end_node != other.end_node || is_complete() != other.is_complete()
                || load > other.load || service_start_time > other.service_start_time
                || reduced_cost > other.reduced_cost) return false;
        return (visited & other.visited) == visited;
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
