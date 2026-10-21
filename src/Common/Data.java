package Common;

/**
 * Stores all input data of a Vehicle Routing Problem with Time Windows instance.
 *
 * <p>Node 0 is the depot and nodes 1 to {@link #customer_num} are customers.
 * Instance data are assigned by the file-reading procedure in {@code FIO}.</p>
 */
public class Data {

    /** Number of customers, excluding the depot. */
    public int customer_num;

    /** Number of nodes, including the depot. */
    public int node_num;

    /** Maximum number of available vehicles. */
    public int vehicle_num;

    /** Capacity of each homogeneous vehicle. */
    public int capacity;

    /** Demand of every node. The depot demand is zero. */
    public int[] demand;

    /** Earliest service starting time of every node. */
    public double[] ready_time;

    /** Latest service starting time of every node. */
    public double[] due_time;

    /** Service duration at every node. */
    public double[] service_time;

    /** Travel cost of every directed arc. */
    public double[][] cost;

    /** Travel time of every directed arc. */
    public double[][] travel_time;
}
