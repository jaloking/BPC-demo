# VRPTW Branch-and-Price-and-Cut 代码框架

## 1. 目录结构

```text
BPC-demo/
├─ README.md
├─ README_BPC.md
├─ build.ps1
├─ .vscode/
├─ .project
├─ .classpath
├─ .settings/
├─ Instances/
├─ Results/
└─ src/
   ├─ Common/
   │  └─ Data.java
   ├─ Model/
   │  ├─ Route.java
   │  ├─ Route_Pool.java
   │  ├─ MP.java
   │  └─ Subset_Row_Cut.java
   ├─ Solver/
   │  ├─ Label.java
   │  ├─ Pricing_Solver.java
   │  ├─ Node.java
   │  ├─ Node_Solver.java
   │  ├─ Cut_Separator.java
   │  ├─ Branch.java
   │  ├─ Solution.java
   │  └─ Solver.java
   ├─ Util/
   │  ├─ FIO.java
   │  ├─ Helper.java
   │  └─ Checker.java
   └─ Test/
      └─ Main.java
```

本项目仅运行 BPC。本文件介绍项目结构、主要字段和方法调用关系。

## 2. 总体调用关系

```text
Test.Main
├─ Util.FIO
└─ Solver.Solver
   ├─ Solver.Node
   ├─ Solver.Node_Solver
   │  ├─ Model.MP
   │  ├─ Solver.Pricing_Solver
   │  │  └─ Solver.Label
   │  └─ Solver.Cut_Separator
   │     └─ Model.Subset_Row_Cut
   ├─ Solver.Branch
   ├─ Solver.Solution
   └─ Util.Checker
```

`MP` 通过 CPLEX 求解当前受限主问题，定价、割分离、分支及搜索树管理由 Java 实现。

## 3. Common 模块

### 3.1 `Data`

`Data` 只保存实例数据，不包含任何成员函数。

主要字段：

- `customer_num`：客户数量；
- `node_num`：包含 depot 的节点数量；
- `vehicle_num`：车辆数量上限；
- `capacity`：车辆容量；
- `demand`：各节点 demand；
- `ready_time`：time window 下界；
- `due_time`：time window 上界；
- `service_time`：service time；
- `cost`：travel-cost matrix；
- `travel_time`：travel-time matrix。

所有数组的创建和数据写入均由 `FIO.read_instance(...)` 完成。

## 4. Model 模块

### 4.1 `Route`

`Route` 表示一条完整路径，同时对应 `MP` 中的一列。

主要字段：

- `nodes`；
- `service_start_times`；
- `cost`；
- `load`。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `set_route_info(Data data)` | 根据 `nodes` 重新计算路径成本、载重和各节点的服务开始时间，并将结果写回当前 `Route`。 |
| `get_customer_coefficient(int customer)` | 返回当前路径在指定客户覆盖约束中的系数，用于构造 RMP 的 route column。 |
| `uses_arc(int from, int to)` | 判断当前路径是否使用有向弧 `(from, to)`，供聚合弧流和分支检查使用。 |
| `is_elementary()` | 检查一条路径是否重复访问客户。pricing 生成的完整路径应满足该条件。 |
| `get_key()` | 根据节点序列生成稳定且唯一的字符串 key，供 `Route_Pool` 去重。 |
| `get_copy()` | 创建当前路径的深拷贝，避免不同节点或 solution 共享可变路径数据。 |

### 4.2 `Route_Pool`

`Route_Pool` 保存已经生成的路径，并维护路径 key 与 route index 的对应关系。

主要字段：

- `routes`；
- `key_to_index`。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `add_route(Route route)` | 将新路径加入池中；如果 key 已存在，则不重复插入并返回原有 index。 |
| `get_route_index(Route route)` | 根据路径 key 查询 route index；路径不存在时返回约定的无效编号。 |
| `get_route(int index)` | 根据 route index 返回对应的 `Route`。 |
| `size()` | 返回当前池中不重复路径的数量。 |
| `clear()` | 清空路径集合和 key-index 映射。 |

### 4.3 `MP`

`MP` 表示一个搜索树节点上的 Restricted Master Problem。

主要字段：

- `data`；
- `node`；
- `route_pool`；
- `cplex`；
- `customer_cons`；
- `vehicle_cons`；
- `cut_cons`；
- `route_vars`；
- `model_route_indices`；
- `artificial_vars`；
- `dual_customers`；
- `dual_vehicle`；
- `dual_cuts`；
- `phase_one`；
- `active_route_indices`；
- `active_route_values`；
- `artificial_values`；
- `objective_value`。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `construct()` | 根据当前 `Node` 和已有 `Route_Pool` 创建初始 RMP，包括约束、人工变量和兼容路径变量。 |
| `add_route_column(int route_index)` | 使用 `IloColumn` 将指定路径动态加入目标函数和相关约束，并维护 route-variable mapping。 |
| `contains_route_column(int route_index)` | 检查指定 route index 是否已经对应当前 RMP 中的一个变量，避免重复加列。 |
| `solve()` | 求解当前 RMP，并保存当前 LP objective。 |
| `set_dual()` | 提取客户、车辆和割约束的对偶值，供定价使用。 |
| `set_value()` | 提取 route variables 和 artificial variables 的取值，并记录所有正值路径。 |
| `is_artificial_feasible()` | 检查人工变量是否全部为零，用于判断当前 RMP solution 是否对应原问题的可行解。 |
| `is_integral()` | 检查当前正值 route variables 是否均为整数。 |
| `clear()` | 释放 CPLEX 模型并清理 RMP 中保存的变量、取值和映射。 |

补充方法：

| 方法 | 作用 |
| --- | --- |
| `artificial(...)` | 创建第一阶段的人工变量。 |
| `start_distance_phase()` | 人工变量为零后将其固定为零，并切换至真实路径成本。 |
| `make_column_space(int additional)` | 列数达到上限时移除取值为零的非基列，路径池保留。 |
| `add_cuts(...)` | 添加割，更新已有列系数，并重新开放人工变量进行第一阶段求解。 |
| `reduced_cost(Route route)` | 计算包含客户、车辆及割对偶贡献的路径检验数。 |

`model_route_indices` 用于维护 CPLEX 变量位置与路径池编号的对应关系。

主问题采用两阶段方法：先最小化人工变量总和，再最小化行驶成本。每个阶段都需要完整定价确认收敛；不能将继承路径不足直接判定为节点不可行。默认最多保留 800 个路径变量。

### 4.4 `Subset_Row_Cut`

`Subset_Row_Cut` 保存三个不同客户组成的子集行割，字段为 `first`、`second`、`third`。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `Subset_Row_Cut(int first, int second, int third)` | 创建按客户编号升序存储的三客户集合。 |
| `contains(int customer)` | 判断客户是否属于该集合。 |
| `coefficient(Route route)` | 返回路径系数 `floor(路径访问该集合的客户数 / 2)`。 |
| `key()` | 返回用于割去重的标识。 |

割约束为 `sum(路径系数 × 路径变量) <= 1`。整数解中至多有一条路径访问该集合的至少两名客户，因此该约束有效；它可以排除三条两客户路径各取 0.5 等分数解。


## 5. Solver 模块

### 5.1 `Label`

`Label` 表示 ESPPRC label-setting 过程中的一条 partial path。

主要字段：

- `end_node`：当前末端节点；
- `visited`：`int` 位掩码，第 i-1 位表示客户 i 是否已访问；
- `load`：累计载重；
- `service_start_time`：末端节点的服务开始时间；
- `reduced_cost`：累计 reduced cost；
- `predecessor`：前驱 label；
- `cut_parity`：`int` 位掩码，第 c 位保存第 c 条活跃割已经访问客户数的奇偶状态。

`Label` 不直接保存完整 path，也不单独保存 actual cost。完整路径通过 `predecessor` 逐层还原。

`visited` 不再分配布尔数组：成员查询使用 `(visited & bit) != 0`，扩展更新使用 `visited | bit`，子集判断使用 `(visited & other.visited) == visited`。支持最多 32 名客户（仓库不占位），超过此规模在 Solver 入口明确报错；`cut_parity` 也使用 int 位掩码，最多支持 32 条活跃割，默认割预算仍为 20 条。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `get_copy()` | 深拷贝当前 label 的资源状态、访问状态和前驱信息，供后续独立扩展。 |
| `extend(...)` | 检查资源与弧限制，扩展标签并更新客户、车辆及割的检验数贡献；第一阶段不累计行驶成本。 |
| `dominates(Label other)` | 在末端和割状态相同的标签间，比较载重、时间、检验数及访问集合，执行安全支配。 |
| `is_complete()` | 判断当前 label 是否已经形成一条完整的 depot-to-depot 路径。 |
| `to_route(Data data)` | 沿 `predecessor` 逐层还原节点序列，并生成完整的 `Route` 对象。 |
| `has_negative_reduced_cost()` | 根据容差判断完整 label 的 reduced cost 是否为负。 |

### 5.2 `Pricing_Solver`

`Pricing_Solver` 管理 labels 并调用 label-setting procedure。

主要字段：

- `data`；
- `labels_at_node`；
- `generated_routes`；
- `best_reduced_cost`；
- `completed`：是否完成完整定价；
- `max_routes_per_call`：每批最多返回的路径数；
- `deadline_nanos`：定价截止时间。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `solve(MP mp)` | 使用主问题对偶值进行标签搜索，保存负检验数路径；可先返回一批路径，完整搜索结束才设置 completed。 |
| `add_non_dominated_label(Label candidate)` | 在相同末端节点的 labels 中执行 dominance 检查，插入 candidate 并删除被其支配的 labels。 |
| `clear()` | 清理上一次 pricing 保存的 labels、生成路径和最小 reduced cost。 |

### 5.3 `Node`

`Node` 保存一个搜索树节点的状态，不保存 CPLEX 对象。

主要字段：

- `data`；
- `id`；
- `parent_id`；
- `depth`；
- `lower_bound`；
- `vehicle_lb`；
- `vehicle_ub`；
- `forbidden_arcs`；
- `route_pool`；
- `cuts`：从父节点继承及当前节点新增的割。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `get_copy()` | 复制车辆界、弧限制、路径池和割列表；不可变割对象可以共享。 |
| `forbid_arc(int from, int to)` | 在 `forbidden_arcs` 中禁止指定有向弧，并使该限制由后代节点继承。 |
| `enforce_arc(int from, int to)` | 通过禁止冲突的进入弧和离开弧来强制指定弧，同时正确处理与 depot 相连的情况。 |
| `is_route_compatible(Route route)` | 检查一条已有路径是否违反当前节点的任意弧限制。 |
| `compareTo(Node other)` | 定义开放节点在 `PriorityQueue` 中的顺序，用于 best-bound node selection。 |

### 5.4 `Node_Solver`

`Node_Solver` 控制一个节点上的两阶段列生成和割分离。

主要字段：

- `data`；
- `pricing_solver`；
- `iteration_count`；
- `pricing_completed`；
- `deadline_nanos`；
- `max_cuts_per_node`；
- `cuts_per_round`；
- `cuts_added`。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `solve(Node node, MP mp)` | 控制两阶段列生成；第二阶段收敛后分离割，必要时重新求解，最终记录节点下界。 |
| `add_generated_routes(Node node, MP mp)` | 将定价生成的路径存入路径池，并将尚未进入当前主问题的路径加列。 |

主要调用关系：

```text
Node_Solver
├─ MP
├─ Pricing_Solver
├─ Route_Pool
└─ Cut_Separator
```

### 5.5 `Branch`

`Branch` 负责分支对象选择和子节点创建。所有分支流程集中在该类中。

主要字段：

- `data`；
- `mp`；
- `left_node`；
- `right_node`；
- `vehicle_value`；
- `from_node`；
- `to_node`；
- `arc_flow`。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `branch(MP mp)` | 读取当前分数解，按既定顺序选择车辆数分支或弧流分支，并生成两个子节点。 |
| `branch_vehicle()` | 计算当前 route variables 使用的车辆数；若其为分数，则分别更新两个子节点的车辆上下界。 |
| `branch_arc()` | 计算 aggregated arc flows，选择分数弧，并创建 forbid-arc 与 enforce-arc 两个分支。 |
| `initialize_child_nodes()` | 复制父节点状态，设置父节点编号、深度和继承下界，为两个分支节点完成初始化。 |

### 5.6 `Solution`

`Solution` 保存一个整数解。

主要字段：

- `objective_value`；
- `routes`。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `set_solution(MP mp)` | 从整数 RMP solution 中提取被选择的路径，并记录目标值。 |
| `get_copy()` | 深拷贝目标值和全部路径，避免 incumbent 与节点路径池共享可变对象。 |

### 5.7 `Solver`

`Solver` 控制完整的 best-bound BPC 搜索。

主要字段：

- `data`；
- `node_queue`；
- `upper_bound`；
- `best_solution`；
- `next_node_id`；
- `solved_node_count`；
- `time_limit_seconds`；
- `optimal`；
- `timed_out`；
- `lower_bound`；
- `verbose`；
- `cuts_added`。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `solve()` | 控制完整 best-bound tree search，包括节点选择、列生成、剪枝、更新 incumbent 和分支。 |
| `create_root_node()` | 创建根节点、单客户路径及节约成本的合并路径，并在可行时建立初始方案和上界。 |
| `is_feasible(Route route)` | 使用 Checker 判断初始或合并路径是否可行。 |
| `update_best_solution(MP mp)` | 当当前 RMP solution 为更优整数解时，更新 `upper_bound` 和 `best_solution`。 |
| `add_children(Branch branch)` | 为两个子节点分配唯一编号，并将其加入开放节点队列。 |

`solve()` 返回是否找到可行方案；是否证明最优由 `optimal` 表示，超时由 `timed_out` 表示。截止时间通过 Java 自带的 `CancellationException` 传递给搜索控制器，不能把超时当作不可行。

### 5.8 `Cut_Separator`

`Cut_Separator` 负责三客户子集行割的分离。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `separate(MP mp, int limit, long deadline)` | 枚举客户三元组，跳过已有割，计算违反程度，并返回指定数量内最违反的割。 |

默认每轮最多新增 3 条，每个节点最多保留 20 条（含继承割），列数上限还会限制割预算。达到预算后仍继续精确定价与分支。新增割通过 `MP.add_cuts()` 加入主问题，并在后续定价中计入割对偶贡献。

## 6. Util 模块

### 6.1 `FIO`

`FIO` 负责实例读取和结果输出。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `read_instance(String file_path)` | 解析实例文件，创建 `Data` 及其全部数组，并写入节点、车辆、成本和时间信息。 |
| `write_solution(Solution solution, String file_path)` | 将目标值、路径数量和各条路径的节点序列输出到指定文件。 |

`read_instance(...)` 负责创建 `Data` 对象、创建所有数组并写入数据。后续加入 Solomon instances 时，只在该类中处理文件格式。

### 6.2 `Helper`

`Helper` 提供统一的数值容差、取整及路径标识工具。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `is_integral(double value)` | 使用统一数值容差判断一个值是否接近整数。 |
| `floor(double value)` | 在容差处理后向下取整，供车辆数分支使用。 |
| `ceiling(double value)` | 在容差处理后向上取整，供车辆数分支使用。 |
| `distance_to_half(double value)` | 计算分数部分与 0.5 的距离，供选择分支对象使用。 |
| `relative_gap(double lower_bound, double upper_bound)` | 根据上下界计算相对 gap。 |
| `route_key(List<Integer> nodes)` | 根据节点序列生成路径去重 key。 |

主要常量：

- `EPS`；
- `RC_EPS`。

### 6.3 `Checker`

`Checker` 用于独立检查路径、整数解和 reduced cost。

主要方法：

| 方法 | 作用 |
| --- | --- |
| `check_route(Data data, Route route)` | 独立检查路径的 depot、容量、时间窗、elementarity、成本和载重。 |
| `check_solution(Data data, Solution solution)` | 检查客户服务次数、车辆数量和 solution objective 是否一致。 |
| `calculate_reduced_cost(...)` | 计算不含割贡献的基础检验数；MP.reduced_cost() 在此基础上扣除割对偶贡献。 |

检查集中在输入读取、初始/合并路径的可行性判断及最终方案。定价在标签扩展时执行资源限制；加列、解提取和输出不再重复执行完整检查。

## 7. Test 模块

### 7.1 `Main`

`Main` 不从命令行参数读取实例路径。实例目录和结果目录直接填写在代码中：

```java
String instance_dir = "Instances";
String result_dir = "Results";
```

主要方法：

| 方法 | 作用 |
| --- | --- |
| `main(String[] args)` | 设置目录和实例名称，依次调用 BPC；不从 args 读取路径。 |
| `run_bpc(String input, String output)` | 读取实例，调用 Solver，输出路径、成本、状态、下界、gap、节点数和求解时间。 |

### 7.2 编译与运行

需要 JDK 和 CPLEX。本机使用 JDK 25 与 CPLEX 20.1，CPLEX 安装路径为 `D:\develop tools\CPlEX 20.1`。

在项目根目录执行：

```powershell
./build.ps1 -Run
```

只编译时执行 `./build.ps1`。脚本输出位于 `.build/classes`；VS Code 的 `Run BPC` 配置在启动前调用脚本编译，再使用该目录运行。编辑器索引使用 `.project`、`.classpath` 与 `.settings` 中的项目配置。

更换安装位置时，调整 `.classpath` 和 `.vscode` 中的 JDK/CPLEX 路径；构建脚本可通过 `-CplexHome` 指定 CPLEX 根目录。

每例默认上限为 300 秒，在 `run_bpc()` 中修改。`Solver.verbose` 默认关闭，控制台每例输出一行摘要。

结果文件为 `Results/实例名_bpc.txt`，记录路径、成本、车辆数和服务时间。末尾的 `Optimal` 表示已证明最优；超时、有无可行解和不可行分别记录。没有可行解时仍会覆盖旧结果并写明状态。

## 8. 建议阅读顺序

```text
Main and FIO / Data initialization
        ↓
Route and Route_Pool
        ↓
Solver.create_root_node
        ↓
MP
        ↓
Label and Pricing_Solver
        ↓
Subset_Row_Cut and Cut_Separator
        ↓
Node_Solver
        ↓
Node and Branch
        ↓
Solution and Solver
        ↓
Checker and FIO.write_solution
```

## 9. Solomon 算例

`Instances` 中包含三个 25-customer Solomon benchmark subinstances：

```text
Instances/
├─ C101_25.txt
├─ R101_25.txt
└─ RC101_25.txt
```

三个文件分别代表 clustered、random 和 random-clustered 三类客户分布，每个文件包含仓库和 25 名客户。

原模板附带参考值位于：

```text
Results/Expected_Results.txt
```

对应文件格式由 `FIO.read_instance(...)` 统一处理。

当前实现使用原始欧氏距离，行驶时间等于距离。客户恰好服务一次，车辆数不超过上限，每条路径满足容量及时间窗；目标为总行驶成本最小。

| 实例 | 总成本 | 车辆数 |
| --- | ---: | ---: |
| C101_25 | 191.8136197787 | 3 |
| R101_25 | 618.3299155463 | 8 |
| RC101_25 | 462.1559467584 | 4 |

参考文件中的 191.3、617.1、461.1 对应每条弧距离向下截断至一位小数的约定；本版保留完整距离精度，不设置另一种运行模式。

## 10. visited 结构与时间对比

第一阶段将 Label.visited 从 boolean[] 改为 int 位掩码；该批对照保留在下列历史记录中。算法流程、支配条件和加割策略保持一致。

控制台每个算例增加 `time=...s`，结果文件末尾增加 `Solve time (s)`。计时范围为
`Solver.solve()`（含初始路径、CPLEX、定价、分支、加割和最终检查），不包含编译、读取实例及结果写入。

修改前后的首次时间、5 次运行的中位数和测量条件见
[visited_timing.md](Results/visited_timing.md)，全部原始记录见
[visited_timing.csv](Results/visited_timing.csv)。

位掩码减少访问集合的分配、复制和逐项比较，但整体耗时仍受 CPLEX、JIT、GC 和机器负载影响，
首次对比中 RC101_25 的中位耗时略有上升，不能据此声称所有算例都更快。

## 11. cut_parity 位掩码与三版本对比

当前 Label.visited 与 Label.cut_parity 均为 int。割状态复制改为整数赋值；访问某条割的客户时，
用按位与判断原状态，用异或翻转该割对应的位；支配判断用整数相等判断全部割状态是否相同。
割对偶值仍使用 double[]，计算方式和割顺序不变。

默认每个节点仍最多保留 20 条割（含继承割），位掩码支持至多 32 条；Node_Solver 限制新增割预算，
Pricing_Solver 拒绝超过 32 条活跃割的输入，避免 Java 移位距离回绕。

三版分别为 boolean[]/boolean[]、int/boolean[]、int/int，均在同一批实验中各运行 6 次，
轮换全部 6 种运行顺序，比较 Solver.solve() 的中位耗时。结果、车辆数、搜索节点数一致。

完整测量条件与对比见 [label_masks_timing.md](Results/label_masks_timing.md)，
54 条原始算例计时记录见 [label_masks_timing.csv](Results/label_masks_timing.csv)。
该批测量不能与第 10 节的旧批次直接混合比较。

## 12. 四组合消融对比

为区分 visited 与 cut_parity 各自的作用，另增加仅 cut_parity 使用 int 的实验版本，
将两个布尔数组、仅 visited int、仅 cut_parity int、双 int 四种组合在同一批中各运行 8 次。
主项目仍使用双 int，本次实验没有切换默认实现。

各组计时范围均为 Solver.solve()；使用轮换及反向顺序平衡先后位置，比较中位数。
结果与测量条件见 [mask_ablation_timing.md](Results/mask_ablation_timing.md)，
96 条原始算例时间见 [mask_ablation_timing.csv](Results/mask_ablation_timing.csv)。
不要将不同批次的绝对时间直接拼接对比。
