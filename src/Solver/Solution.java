package Solver;

import Model.MP;
import Model.Route;

import java.util.ArrayList;

/**
 * Stores one integer VRPTW solution.
 */
public class Solution {

    public double objective_value;
    public ArrayList<Route> routes;

    /** Extracts an integer solution from a solved RMP. */
    public void set_solution(MP mp) {
        if (!mp.is_integral()) throw new IllegalArgumentException("只能从可行整数 RMP 提取解。");
        routes = new ArrayList<>();
        objective_value = 0.0;
        for (int k = 0; k < mp.active_route_indices.size(); k++) {
            int count = (int) Math.round(mp.active_route_values.get(k));
            Route route = mp.route_pool.get_route(mp.active_route_indices.get(k));
            for (int t = 0; t < count; t++) {
                routes.add(route.get_copy());
                objective_value += route.cost;
            }
        }
    }

    /** Returns a deep copy of this solution. */
    public Solution get_copy() {
        Solution copy = new Solution();
        copy.objective_value = objective_value;
        copy.routes = new ArrayList<>();
        if (routes != null) for (Route route : routes) copy.routes.add(route.get_copy());
        return copy;
    }
}
