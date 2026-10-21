package Model;

import Common.Data;
import Solver.Node;
import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import ilog.concert.IloRange;
import ilog.concert.IloColumn;
import ilog.concert.IloObjective;
import ilog.cplex.IloCplex;
import Util.Helper;
import Util.Checker;
import java.util.concurrent.CancellationException;

import java.util.ArrayList;

/**
 * Defines the Restricted Master Problem associated with one search-tree node.
 */
public class MP {

    public Data data;
    public Node node;
    public Route_Pool route_pool;
    public IloCplex cplex;

    public IloRange[] customer_cons;
    public IloRange vehicle_cons;
    public ArrayList<IloRange> cut_cons = new ArrayList<>();
    public double[] dual_cuts = new double[0];

    /** Route variables currently included in the RMP. */
    public ArrayList<IloNumVar> route_vars;

    /** Route-pool index associated with every route variable. */
    public ArrayList<Integer> model_route_indices;

    public IloNumVar[] artificial_vars;

    public double[] dual_customers;
    public double dual_vehicle;

    public ArrayList<Integer> active_route_indices;
    public ArrayList<Double> active_route_values;
    public double[] artificial_values;

    public double objective_value;
    public boolean phase_one;
    private IloObjective objective;
    /** Manage inactive columns to keep each RMP small. The route pool is retained. */
    public int max_model_columns = 800;

    /** Constructs the initial RMP for the current node. */
    public void construct() throws IloException {
        if (data == null || node == null || node.route_pool == null)
            throw new IllegalStateException("主问题缺少实例或节点。");
        if (max_model_columns < data.customer_num + 2)
            throw new IllegalArgumentException("列数上限至少应为客户数加 2。");
        if (cplex != null) clear();
        route_pool = node.route_pool;
        route_vars = new ArrayList<>();
        model_route_indices = new ArrayList<>();
        active_route_indices = new ArrayList<>();
        active_route_values = new ArrayList<>();
        dual_customers = new double[data.node_num];
        phase_one = true;
        cplex = new IloCplex();
        cplex.setOut(null);
        cplex.setParam(IloCplex.Param.Threads, 1);
        cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Primal);
        cplex.setParam(IloCplex.Param.Simplex.Tolerances.Optimality, 1.0e-9);
        cplex.setParam(IloCplex.Param.Simplex.Tolerances.Feasibility, 1.0e-9);
        objective = cplex.addMinimize();
        customer_cons = new IloRange[data.node_num];
        for (int i = 1; i < data.node_num; i++)
            customer_cons[i] = cplex.addRange(1.0, 1.0, "customer_" + i);
        vehicle_cons = cplex.addRange(node.vehicle_lb, node.vehicle_ub, "vehicles");
        cut_cons = new ArrayList<>();
        for (Subset_Row_Cut cut : node.cuts)
            cut_cons.add(cplex.addRange(-Double.MAX_VALUE, 1.0, "src_" + cut.key()));
        dual_cuts = new double[cut_cons.size()];
        // Signed artificials make phase I feasible even with an inherited vehicle lower bound.
        artificial_vars = new IloNumVar[2 * data.customer_num + 2];
        artificial_values = new double[artificial_vars.length];
        int position = 0;
        for (int i = 1; i < data.node_num; i++) {
            artificial_vars[position++] = artificial(customer_cons[i], 1.0, "cover_plus_" + i);
            artificial_vars[position++] = artificial(customer_cons[i], -1.0, "cover_minus_" + i);
        }
        artificial_vars[position++] = artificial(vehicle_cons, 1.0, "vehicles_plus");
        artificial_vars[position] = artificial(vehicle_cons, -1.0, "vehicles_minus");
        for (int r = route_pool.size() - 1; r >= 0 && route_vars.size() < max_model_columns; r--)
            if (node.is_route_compatible(route_pool.get_route(r))) add_route_column(r);
    }

    /** Removing only zero nonbasic columns preserves the current feasible basis. */
    public void make_column_space(int additional) throws IloException {
        int removeCount = route_vars.size() + additional - max_model_columns;
        if (removeCount <= 0) return;
        ArrayList<Integer> remove = new ArrayList<>();
        for (int k = 0; k < route_vars.size() && remove.size() < removeCount; k++) {
            IloNumVar variable = route_vars.get(k);
            if (cplex.getBasisStatus(variable) != IloCplex.BasisStatus.Basic
                    && Math.abs(cplex.getValue(variable)) < 1.0e-12) remove.add(k);
        }
        if (remove.size() < removeCount)
            throw new IllegalStateException("活跃基变量过多，请增大 MP.max_model_columns。");
        for (int p = remove.size() - 1; p >= 0; p--) {
            int k = remove.get(p);
            cplex.delete(route_vars.remove(k));
            model_route_indices.remove(k);
        }
        // Evicted routes can be priced and reinserted. No conclusion uses a partial pricing search.
    }

    private IloNumVar artificial(IloRange range, double coefficient, String name) throws IloException {
        IloColumn column = cplex.column(objective, 1.0).and(cplex.column(range, coefficient));
        return cplex.numVar(column, 0.0, Double.MAX_VALUE, name);
    }

    /** Fix artificials to zero and replace phase-I costs by actual route costs. */
    public void start_distance_phase() throws IloException {
        if (!is_artificial_feasible()) throw new IllegalStateException("第一阶段尚未找到可行解。");
        for (IloNumVar variable : artificial_vars) {
            variable.setUB(0.0);
            cplex.setLinearCoef(objective, variable, 0.0);
        }
        for (int k = 0; k < route_vars.size(); k++)
            cplex.setLinearCoef(objective, route_vars.get(k),
                    route_pool.get_route(model_route_indices.get(k)).cost);
        phase_one = false;
    }

    /** Adds one route column to the current RMP. */
    public int add_route_column(int route_index) throws IloException {
        int existing = model_route_indices.indexOf(route_index);
        if (existing >= 0) return existing;
        Route route = route_pool.get_route(route_index);
        IloColumn column = cplex.column(objective, phase_one ? 0.0 : route.cost)
                .and(cplex.column(vehicle_cons, 1.0));
        for (int i = 1; i < data.node_num; i++) {
            int coefficient = route.get_customer_coefficient(i);
            if (coefficient != 0) column = column.and(cplex.column(customer_cons[i], coefficient));
        }
        for (int c = 0; c < cut_cons.size(); c++) {
            int coefficient = node.cuts.get(c).coefficient(route);
            if (coefficient != 0) column = column.and(cplex.column(cut_cons.get(c), coefficient));
        }
        IloNumVar variable = cplex.numVar(column, 0.0, Double.MAX_VALUE, "route_" + route_index);
        route_vars.add(variable);
        model_route_indices.add(route_index);
        return route_vars.size() - 1;
    }

    /** Returns whether the route already has a variable in the current RMP. */
    public boolean contains_route_column(int route_index) {
        return model_route_indices.contains(route_index);
    }

    /** Solves the current RMP. */
    public boolean solve() throws IloException {
        boolean solved = cplex.solve();
        if (cplex.getCplexStatus() == IloCplex.CplexStatus.AbortTimeLim)
            throw new CancellationException("Time limit reached.");
        if (cplex.getStatus() == IloCplex.Status.Infeasible) return false;
        if (!solved || cplex.getStatus() != IloCplex.Status.Optimal)
            throw new IllegalStateException("RMP 未达到最优状态：" + cplex.getStatus());
        objective_value = cplex.getObjValue();
        return true;
    }

    /** Retrieves dual values required by pricing. */
    public void set_dual() throws IloException {
        for (int i = 1; i < data.node_num; i++) dual_customers[i] = cplex.getDual(customer_cons[i]);
        dual_vehicle = cplex.getDual(vehicle_cons);
        for (int c = 0; c < cut_cons.size(); c++) dual_cuts[c] = cplex.getDual(cut_cons.get(c));
    }

    /** Add rows to existing columns; all future columns receive the same coefficients. */
    public void add_cuts(ArrayList<Subset_Row_Cut> cuts) throws IloException {
        for (Subset_Row_Cut cut : cuts) {
            if (node.cuts.stream().anyMatch(old -> old.key().equals(cut.key()))) continue;
            IloRange row = cplex.addRange(-Double.MAX_VALUE, 1.0, "src_" + cut.key());
            node.cuts.add(cut);
            cut_cons.add(row);
            for (int r = 0; r < route_vars.size(); r++)
                cplex.setLinearCoef(row, route_vars.get(r),
                        cut.coefficient(route_pool.get_route(model_route_indices.get(r))));
        }
        dual_cuts = new double[cut_cons.size()];
        // New cuts can make the restricted column set infeasible. Restart exact phase I.
        for (IloNumVar variable : artificial_vars) {
            variable.setUB(Double.MAX_VALUE);
            cplex.setLinearCoef(objective, variable, 1.0);
        }
        for (IloNumVar variable : route_vars) cplex.setLinearCoef(objective, variable, 0.0);
        phase_one = true;
    }

    public double reduced_cost(Route route) {
        double result = Checker.calculate_reduced_cost(route, dual_customers, dual_vehicle);
        if (phase_one) result -= route.cost;
        for (int c = 0; c < dual_cuts.length; c++)
            result -= dual_cuts[c] * node.cuts.get(c).coefficient(route);
        return result;
    }

    /** Retrieves route-variable and artificial-variable values. */
    public void set_value() throws IloException {
        active_route_indices.clear();
        active_route_values.clear();
        for (int k = 0; k < route_vars.size(); k++) {
            double value = cplex.getValue(route_vars.get(k));
            if (value > Helper.EPS) {
                active_route_indices.add(model_route_indices.get(k));
                active_route_values.add(value);
            }
        }
        for (int k = 0; k < artificial_vars.length; k++)
            artificial_values[k] = cplex.getValue(artificial_vars[k]);
    }

    /** Returns whether all artificial variables are zero. */
    public boolean is_artificial_feasible() {
        double sum = 0.0;
        for (double value : artificial_values) sum += Math.abs(value);
        return sum <= Helper.EPS;
    }

    /** Returns whether all positive route variables are integral. */
    public boolean is_integral() {
        if (phase_one || !is_artificial_feasible()) return false;
        for (double value : active_route_values) if (!Helper.is_integral(value)) return false;
        return true;
    }

    /** Releases the RMP and its stored solution data. */
    public void clear() {
        if (cplex != null) cplex.end();
        cplex = null;
        objective = null;
        customer_cons = null;
        vehicle_cons = null;
        cut_cons.clear();
        dual_cuts = new double[0];
        artificial_vars = null;
        artificial_values = null;
        dual_customers = null;
        dual_vehicle = 0.0;
        if (route_vars != null) route_vars.clear();
        if (model_route_indices != null) model_route_indices.clear();
        if (active_route_indices != null) active_route_indices.clear();
        if (active_route_values != null) active_route_values.clear();
        objective_value = Double.NaN;
    }
}
