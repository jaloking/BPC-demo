package Model;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Stores generated routes and assigns a stable integer index to every route.
 */
public class Route_Pool {

    /** All routes currently stored in the pool. */
    public ArrayList<Route> routes = new ArrayList<>();

    /** Mapping from a route key to its index in routes. */
    public HashMap<String, Integer> key_to_index = new HashMap<>();

    /** Adds a route and returns its existing or newly created index. */
    public int add_route(Route route) {
        String key = route.get_key();
        Integer existing = key_to_index.get(key);
        if (existing != null) return existing;
        int index = routes.size();
        routes.add(route.get_copy());
        key_to_index.put(key, index);
        return index;
    }

    /** Returns the index of a route, or -1 if the route is absent. */
    public int get_route_index(Route route) {
        return key_to_index.getOrDefault(route.get_key(), -1);
    }

    /** Returns the route associated with an index. */
    public Route get_route(int index) {
        return routes.get(index);
    }

    /** Returns the number of stored routes. */
    public int size() {
        return routes.size();
    }

    /** Releases all data stored in the route pool. */
    public void clear() {
        routes.clear();
        key_to_index.clear();
    }
}
