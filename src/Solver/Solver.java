package Solver;

import Common.Data;
import Model.MP;
import Model.Route;
import Model.Route_Pool;
import Util.Checker;
import Util.Helper;
import java.util.concurrent.CancellationException;
import ilog.concert.IloException;

import java.util.PriorityQueue;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Controls the complete best-bound branch-price-and-cut search.
 */
public class Solver {

    public Data data;
    public PriorityQueue<Node> node_queue;

    public double upper_bound;
    public Solution best_solution;

    public int next_node_id;
    public int solved_node_count;
    public double time_limit_seconds = 300.0;
    public boolean optimal;
    public boolean timed_out;
    public double lower_bound;
    public boolean verbose;
    public int cuts_added;

    /** Solves the instance by branch-price-and-cut. */
    public boolean solve() throws IloException {
        if (data == null) throw new IllegalStateException("求解器缺少实例数据。");
        if (!(time_limit_seconds > 0.0)) throw new IllegalArgumentException("时间上限必须大于 0。");
        node_queue = new PriorityQueue<>();
        upper_bound = Double.POSITIVE_INFINITY;
        best_solution = null;
        next_node_id = 0;
        solved_node_count = 0;
        cuts_added = 0;
        optimal = false;
        timed_out = false;
        lower_bound = 0.0;
        long start = System.nanoTime();
        long budget = time_limit_seconds >= Long.MAX_VALUE / 1.0e9
                ? Long.MAX_VALUE : (long) (time_limit_seconds * 1.0e9);
        long deadline = budget == Long.MAX_VALUE ? Long.MAX_VALUE : start + budget;
        node_queue.add(create_root_node());
        Node_Solver nodeSolver = new Node_Solver();
        nodeSolver.deadline_nanos = deadline;
        while (!node_queue.isEmpty()) {
            if (System.nanoTime() >= deadline) {
                timed_out = true;
                break;
            }
            Node node = node_queue.poll();
            if (node.lower_bound >= upper_bound - Helper.EPS) continue;
            MP mp = new MP();
            try {
                if (!nodeSolver.solve(node, mp)) {
                    solved_node_count++;
                    continue;
                }
                solved_node_count++;
                if (verbose) System.out.printf("Node %d, depth %d, LP %.6f, columns %d, iterations %d%n",
                        node.id, node.depth, mp.objective_value, mp.route_vars.size(), nodeSolver.iteration_count);
                if (mp.objective_value >= upper_bound - Helper.EPS) continue;
                if (mp.is_integral()) update_best_solution(mp);
                else {
                    Branch branch = new Branch();
                    if (!branch.branch(mp))
                        throw new IllegalStateException("分数路径解没有可用的车辆数或弧流分支。");
                    add_children(branch);
                }
            } catch (CancellationException e) {
                node_queue.add(node); // Its inherited bound remains valid.
                timed_out = true;
                break;
            } finally {
                cuts_added += nodeSolver.cuts_added;
                mp.clear();
            }
        }
        if (!timed_out) {
            optimal = best_solution != null;
            lower_bound = upper_bound;
        } else {
            lower_bound = node_queue.isEmpty() ? upper_bound
                    : Math.min(upper_bound, node_queue.peek().lower_bound);
        }
        if (best_solution != null) Checker.check_solution(data, best_solution);
        // True means a feasible incumbent exists, not necessarily proven optimal.
        return best_solution != null;
    }

    /** Creates the root node and its initial route set. */
    public Node create_root_node() {
        Node root = new Node();
        root.data = data;
        root.id = next_node_id++;
        root.parent_id = -1;
        root.depth = 0;
        root.lower_bound = 0.0;
        long demandSum = 0;
        for (int i = 1; i < data.node_num; i++) demandSum += data.demand[i];
        root.vehicle_lb = Math.max(1, (int) ((demandSum + data.capacity - 1L) / data.capacity));
        root.vehicle_ub = Math.min(data.vehicle_num, data.customer_num);
        root.forbidden_arcs = new boolean[data.node_num][data.node_num];
        for (int i = 0; i < data.node_num; i++) root.forbidden_arcs[i][i] = true;
        root.route_pool = new Route_Pool();
        ArrayList<Route> heuristic = new ArrayList<>();
        for (int i = 1; i < data.node_num; i++) {
            Route route = new Route();
            route.nodes = new ArrayList<>(Arrays.asList(0, i, 0));
            route.set_route_info(data);
            if (is_feasible(route)) {
                root.route_pool.add_route(route);
                heuristic.add(route);
            }
        }
        // Savings merges provide useful starting columns and, when possible, an incumbent.
        if (heuristic.size() == data.customer_num) {
            while (true) {
                Route bestMerge = null;
                int first = -1, second = -1;
                double bestSaving = Helper.EPS;
                for (int i = 0; i < heuristic.size(); i++) {
                    for (int j = 0; j < heuristic.size(); j++) {
                        if (i == j) continue;
                        Route a = heuristic.get(i), b = heuristic.get(j);
                        if (a.load + b.load > data.capacity) continue;
                        Route merged = new Route();
                        merged.nodes = new ArrayList<>(a.nodes.subList(0, a.nodes.size() - 1));
                        merged.nodes.addAll(b.nodes.subList(1, b.nodes.size()));
                        merged.set_route_info(data);
                        double saving = a.cost + b.cost - merged.cost;
                        if (saving > bestSaving && is_feasible(merged)) {
                            bestSaving = saving;
                            bestMerge = merged;
                            first = i;
                            second = j;
                        }
                    }
                }
                if (bestMerge == null) break;
                heuristic.remove(Math.max(first, second));
                heuristic.remove(Math.min(first, second));
                heuristic.add(bestMerge);
                root.route_pool.add_route(bestMerge);
            }
            if (heuristic.size() <= data.vehicle_num) {
                Solution solution = new Solution();
                solution.routes = heuristic;
                solution.objective_value = 0.0;
                for (Route route : heuristic) solution.objective_value += route.cost;
                best_solution = solution.get_copy();
                upper_bound = best_solution.objective_value;
            }
        }
        return root;
    }

    private boolean is_feasible(Route route) {
        try {
            Checker.check_route(data, route);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Updates the incumbent from an integer RMP solution. */
    private void update_best_solution(MP mp) {
        Solution solution = new Solution();
        solution.set_solution(mp);
        if (solution.objective_value < upper_bound - Helper.EPS) {
            best_solution = solution.get_copy();
            upper_bound = best_solution.objective_value;
        }
    }

    /** Assigns node identifiers and inserts both children into the open queue. */
    private void add_children(Branch branch) {
        for (Node child : Arrays.asList(branch.left_node, branch.right_node)) {
            if (child == null || child.vehicle_lb > child.vehicle_ub
                    || child.lower_bound >= upper_bound - Helper.EPS) continue;
            child.id = next_node_id++;
            node_queue.add(child);
        }
    }
}
