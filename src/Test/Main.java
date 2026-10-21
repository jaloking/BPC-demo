package Test;

import Solver.Solver;
import Util.FIO;
import Util.Helper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** Runs BPC for the selected instances. */
public class Main {
    public static void main(String[] args) throws Exception {
        String instance_dir = "Instances";
        String result_dir = "Results";
        String[] names = {"C101_25", "R101_25", "RC101_25"};
        for (String name : names)
            run_bpc(Paths.get(instance_dir, name + ".txt").toString(),
                    Paths.get(result_dir, name + "_bpc.txt").toString());
    }

    private static void run_bpc(String input, String output) throws Exception {
        Solver solver = new Solver();
        solver.data = FIO.read_instance(input);
        solver.time_limit_seconds = 300.0;
        boolean feasible = solver.solve();
        String status = solver.optimal ? "Optimal"
                : solver.timed_out ? (feasible ? "Time limit, feasible" : "Time limit, no solution")
                : "Infeasible";
        String summary = "Algorithm: BPC, Status: " + status
                + ", Lower bound: " + solver.lower_bound
                + ", Relative gap: " + Helper.relative_gap(solver.lower_bound, solver.upper_bound)
                + ", Nodes: " + solver.solved_node_count;
        if (feasible) FIO.write_solution(solver.best_solution, output);
        else {
            Files.createDirectories(Paths.get(output).toAbsolutePath().getParent());
            Files.write(Paths.get(output), Arrays.asList("No feasible solution returned."), StandardCharsets.UTF_8);
        }
        Files.write(Paths.get(output), Arrays.asList(summary), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        System.out.println(Paths.get(input).getFileName() + ": " + status
                + (feasible ? ", cost=" + solver.upper_bound + ", vehicles=" + solver.best_solution.routes.size() : "")
                + ", nodes=" + solver.solved_node_count);
    }
}
