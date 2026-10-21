package Util;

import Common.Data;
import Model.Route;
import Solver.Solution;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Instance input and BPC solution output. */
public final class FIO {
    public static Data read_instance(String file) throws IOException {
        Data data = new Data();
        List<String[]> rows = new ArrayList<>();
        int section = 0;
        boolean vehicleRead = false;
        try {
            for (String raw : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (line.equals("VEHICLE")) { section = 1; continue; }
                if (line.equals("CUSTOMER")) { section = 2; continue; }
                if (section == 0 || line.startsWith("NUMBER") || line.startsWith("CUST")) continue;
                String[] values = line.split("\s+");
                if (section == 1) {
                    if (vehicleRead || values.length != 2) throw new IOException("Invalid vehicle row.");
                    data.vehicle_num = Integer.parseInt(values[0]);
                    data.capacity = Integer.parseInt(values[1]);
                    vehicleRead = true;
                } else {
                    if (values.length != 7) throw new IOException("A customer row needs seven columns.");
                    rows.add(values);
                }
            }
            if (!vehicleRead || data.vehicle_num <= 0 || data.capacity <= 0 || rows.size() < 2)
                throw new IOException("Missing or invalid vehicle/customer data.");
            int n = rows.size();
            data.node_num = n;
            data.customer_num = n - 1;
            data.demand = new int[n];
            data.ready_time = new double[n];
            data.due_time = new double[n];
            data.service_time = new double[n];
            data.cost = new double[n][n];
            data.travel_time = new double[n][n];
            double[] x = new double[n], y = new double[n];
            boolean[] seen = new boolean[n];
            for (String[] row : rows) {
                int id = Integer.parseInt(row[0]);
                if (id < 0 || id >= n || seen[id]) throw new IOException("Invalid or repeated node ID: " + id);
                seen[id] = true;
                x[id] = Double.parseDouble(row[1]);
                y[id] = Double.parseDouble(row[2]);
                data.demand[id] = Integer.parseInt(row[3]);
                data.ready_time[id] = Double.parseDouble(row[4]);
                data.due_time[id] = Double.parseDouble(row[5]);
                data.service_time[id] = Double.parseDouble(row[6]);
                if (!Double.isFinite(x[id]) || !Double.isFinite(y[id])
                        || !Double.isFinite(data.ready_time[id]) || !Double.isFinite(data.due_time[id])
                        || !Double.isFinite(data.service_time[id]) || data.demand[id] < 0
                        || data.service_time[id] < 0 || data.ready_time[id] > data.due_time[id])
                    throw new IOException("Invalid node values: " + id);
            }
            if (data.demand[0] != 0) throw new IOException("Depot demand must be zero.");
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) {
                    double distance = Math.hypot(x[i] - x[j], y[i] - y[j]);
                    if (!Double.isFinite(distance)) throw new IOException("Distance overflow.");
                    data.cost[i][j] = distance;
                    data.travel_time[i][j] = distance;
                }
            return data;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid number in instance: " + file, e);
        }
    }

    public static void write_solution(Solution solution, String file) throws IOException {
        Path path = Paths.get(file);
        Files.createDirectories(path.toAbsolutePath().getParent());
        List<String> lines = new ArrayList<>();
        lines.add("Objective: " + solution.objective_value);
        lines.add("Vehicles: " + solution.routes.size());
        for (int i = 0; i < solution.routes.size(); i++) {
            Route route = solution.routes.get(i);
            lines.add("Route " + (i + 1) + ": " + route.nodes);
            lines.add("  Cost: " + route.cost + ", Load: " + route.load);
            lines.add("  Service start times: " + route.service_start_times);
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }
}
