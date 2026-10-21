package Model;

import Common.Data;
import Util.Helper;

import java.util.ArrayList;
import java.util.HashSet;


/**
 * Represents one complete depot-to-depot route and one column of the master problem.
 */
public class Route {

    /** Complete node sequence, including the depot at both ends. */
    public ArrayList<Integer> nodes;

    /** Service starting time at every position of the node sequence. */
    public ArrayList<Double> service_start_times;

    /** Total travel cost of the route. */
    public double cost;

    /** Total demand served by the route. */
    public int load;

    /** Recomputes all route information from the node sequence. */
    public void set_route_info(Data data) {
        // 每次调用都重新计算，避免重复累加。
        cost = 0.0;
        load = 0;
        service_start_times = new ArrayList<>();

        // 采用最早可出发时间。
        double currentTime = data.ready_time[0];
        service_start_times.add(currentTime);

        for (int p = 1; p < nodes.size(); p++) {
            int from = nodes.get(p - 1);
            int to = nodes.get(p);

            // 累加这条弧的行驶成本。
            cost += data.cost[from][to];

            // 客户需求计入载重，仓库不计入。
            if (to != 0) {
                load += data.demand[to];
            }

            // 上一个节点服务结束后，再行驶到当前节点。
            double arrivalTime = currentTime
                    + data.service_time[from]
                    + data.travel_time[from][to];

            // 提前到达时，等待到时间窗开始。
            currentTime = Math.max(arrivalTime, data.ready_time[to]);

            // 保存当前位置的服务开始时间。
            service_start_times.add(currentTime);
        }
    }

    /** Returns the coefficient of this route in one customer-covering constraint. */
    public int get_customer_coefficient(int customer) {
        int count = 0;

        for (int node : nodes) {
            if (node == customer) {
                count++;
            }
        }

        return count;
    }

    /** Returns whether this route uses the directed arc (from, to). */
    public boolean uses_arc(int from, int to) {
        for (int p = 0; p < nodes.size() - 1; p++) {
            int current = nodes.get(p);
            int next = nodes.get(p + 1);

            if (current == from && next == to) {
                return true;
            }
        }

        return false;
    }

    /** Returns whether every customer is visited at most once. */
    public boolean is_elementary() {
        if (nodes == null) return false;
        HashSet<Integer> visited = new HashSet<>();
        for (Integer customer : nodes) {
            if (customer == null || customer < 0) return false;
            if (customer != 0 && !visited.add(customer)) return false;
        }
        return true;
    }

    /** Returns the key used by Route_Pool for duplicate detection. */
    public String get_key() {
        return Helper.route_key(nodes);
    }

    /** Returns a deep copy of this route. */
    public Route get_copy() {
        Route copy = new Route();
        copy.nodes = nodes == null ? null : new ArrayList<>(nodes);
        copy.service_start_times = service_start_times == null
                ? null : new ArrayList<>(service_start_times);
        copy.cost = cost;
        copy.load = load;
        return copy;
    }
}
