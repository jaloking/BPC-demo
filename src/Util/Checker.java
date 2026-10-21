package Util;

import Common.Data;
import Model.Route;
import Solver.Solution;

/**
 * Independently checks generated routes, reduced costs, and integer solutions.
 */
public final class Checker {

    /** Checks capacity, time windows, elementarity, route cost, and route load. */
    public static void check_route(Data data, Route route) {
        if (route == null || route.nodes == null || route.nodes.size() < 3)
            throw new IllegalArgumentException("路径必须至少包含仓库、客户和仓库。");
        int positions = route.nodes.size();
        if (route.nodes.get(0) == null || route.nodes.get(positions - 1) == null
                || route.nodes.get(0) != 0 || route.nodes.get(positions - 1) != 0)
            throw new IllegalArgumentException("路径必须从仓库出发并返回仓库。");
        if (route.service_start_times == null || route.service_start_times.size() != positions)
            throw new IllegalArgumentException("服务时间列表与路径长度不一致。");
        boolean[] seen = new boolean[data.node_num];
        int expectedLoad = 0;
        double expectedCost = 0.0;
        double previousStored = 0.0;
        for (int p = 0; p < positions; p++) {
            Integer id = route.nodes.get(p);
            if (id == null || id < 0 || id >= data.node_num)
                throw new IllegalArgumentException("无效节点编号：" + id);
            if (p > 0 && p < positions - 1) {
                if (id == 0 || seen[id])
                    throw new IllegalArgumentException("中途访问仓库或重复访问客户：" + id);
                seen[id] = true;
                expectedLoad += data.demand[id];
            }
            if (p > 0) {
                int from = route.nodes.get(p - 1);
                expectedCost += data.cost[from][id];
            }
            Double stored = route.service_start_times.get(p);
            if (stored == null || !Double.isFinite(stored)
                    || stored < data.ready_time[id] - Helper.EPS
                    || stored > data.due_time[id] + Helper.EPS)
                throw new IllegalArgumentException("节点时间窗不满足：" + id);
            if (p > 0) {
                int from = route.nodes.get(p - 1);
                double arrival = previousStored + data.service_time[from] + data.travel_time[from][id];
                if (stored < arrival - Helper.EPS)
                    throw new IllegalArgumentException("服务时间不满足行驶时间递推：" + id);
            }
            previousStored = stored;
        }
        if (expectedLoad > data.capacity || route.load != expectedLoad)
            throw new IllegalArgumentException("容量超限或路径载重记录错误。");
        if (!Double.isFinite(route.cost)
                || Math.abs(route.cost - expectedCost) > Helper.EPS * Math.max(1.0, expectedCost))
            throw new IllegalArgumentException("路径成本记录错误。");
    }

    /** Checks customer service, vehicle number, and objective value. */
    public static void check_solution(Data data, Solution solution) {
        if (solution == null || solution.routes == null)
            throw new IllegalArgumentException("解尚未初始化。");
        if (solution.routes.size() > data.vehicle_num)
            throw new IllegalArgumentException("解使用的车辆数超出上限。");
        int[] coverage = new int[data.node_num];
        double totalCost = 0.0;
        for (Route route : solution.routes) {
            check_route(data, route);
            for (int p = 1; p < route.nodes.size() - 1; p++)
                coverage[route.nodes.get(p)]++;
            totalCost += route.cost;
        }
        for (int i = 1; i < data.node_num; i++) {
            if (coverage[i] != 1)
                throw new IllegalArgumentException("客户 " + i + " 的服务次数不是 1。");
        }
        if (!Double.isFinite(solution.objective_value)
                || Math.abs(solution.objective_value - totalCost) > Helper.EPS * Math.max(1.0, totalCost))
            throw new IllegalArgumentException("解的目标值与路径成本总和不一致。");
    }

    /** Computes the reduced cost of a route from master-problem dual values. */
    public static double calculate_reduced_cost(Route route, double[] dual_customers,
                                                double dual_vehicle) {
        double result = route.cost - dual_vehicle;
        for (int p = 1; p < route.nodes.size() - 1; p++)
            result -= dual_customers[route.nodes.get(p)];
        return result;
    }
}
