package Solver;

import Common.Data;
import Model.MP;
import Model.Route;
import java.util.concurrent.CancellationException;
import ilog.concert.IloException;

/**
 * Controls column generation at one branch-price-and-cut node.
 */
public class Node_Solver {

    public Data data;
    public Pricing_Solver pricing_solver;

    public int iteration_count;
    public boolean pricing_completed;
    public long deadline_nanos = Long.MAX_VALUE;
    public int max_cuts_per_node = 20;
    public int cuts_per_round = 3;
    public int cuts_added;

    /** Solves the LP relaxation of one node by column generation. */
    public boolean solve(Node node, MP mp) throws IloException {
        data = node.data;
        iteration_count = 0;
        pricing_completed = false;
        cuts_added = 0;
        if (node.vehicle_lb > node.vehicle_ub) return false;
        mp.data = data;
        mp.node = node;
        mp.construct();
        pricing_solver = new Pricing_Solver();
        pricing_solver.deadline_nanos = deadline_nanos;
        pricing_solver.max_routes_per_call = Math.min(100, mp.max_model_columns - data.customer_num - 1);
        while (true) {
            if (System.nanoTime() >= deadline_nanos) throw new CancellationException("Time limit reached.");
            iteration_count++;
            if (deadline_nanos != Long.MAX_VALUE)
                mp.cplex.setParam(ilog.cplex.IloCplex.Param.TimeLimit,
                        Math.max(1.0e-6, (deadline_nanos - System.nanoTime()) / 1.0e9));
            if (!mp.solve()) return false;
            mp.set_value();
            mp.set_dual();
            pricing_solver.max_routes_per_call = Math.max(1, Math.min(100,
                    mp.max_model_columns - data.customer_num - 1 - node.cuts.size()));
            boolean found = pricing_solver.solve(mp);
            if (found) {
                mp.make_column_space(pricing_solver.generated_routes.size());
                int oldSize = mp.route_vars.size();
                add_generated_routes(node, mp);
                if (oldSize == mp.route_vars.size())
                    throw new IllegalStateException("定价返回负检验数路径，但没有新增列。");
                continue;
            }
            if (!pricing_solver.completed)
                throw new IllegalStateException("定价提前结束，不能宣称列生成收敛。");
            if (mp.phase_one) {
                if (!mp.is_artificial_feasible()) return false;
                mp.start_distance_phase();
                continue;
            }
            if (!mp.is_integral()) {
                java.util.ArrayList<Model.Subset_Row_Cut> cuts = new Cut_Separator().separate(mp,
                        Math.min(cuts_per_round, Math.min(Math.min(max_cuts_per_node, Integer.SIZE),
                                mp.max_model_columns - data.customer_num - 2) - node.cuts.size()), deadline_nanos);
                if (!cuts.isEmpty()) {
                    mp.add_cuts(cuts);
                    cuts_added += cuts.size();
                    continue;
                }
            }
            pricing_completed = true;
            node.lower_bound = mp.objective_value;
            return true;
        }
    }

    /** Adds the pricing routes to the route pool and the current RMP. */
    private void add_generated_routes(Node node, MP mp) throws IloException {
        for (Route route : pricing_solver.generated_routes) {
            int index = node.route_pool.add_route(route);
            if (!mp.contains_route_column(index)) mp.add_route_column(index);
        }
    }
}
