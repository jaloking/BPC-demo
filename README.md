# BPC-demo

使用 Java 和 IBM ILOG CPLEX 求解带时间窗的车辆路径问题（VRPTW）。

实现分支定价割（BPC）：两阶段列生成、标签定价、三客户子集行割、车辆数及弧流分支。
目标是最小化总行驶成本，满足客户唯一服务、车辆容量、时间窗和车辆数量限制。

## 环境

- JDK 25
- IBM ILOG CPLEX 20.1（需自行安装并具备可用许可）
- Windows PowerShell；VS Code 为可选编辑器

CPLEX 本机默认路径为 `D:\develop tools\CPlEX 20.1`。在其他电脑运行时，通过
`CPLEX_STUDIO_DIR201` 或脚本的 `-CplexHome` 参数指定安装位置；VS Code / Eclipse
配置中的 JDK、CPLEX 路径也需要按本机位置调整。

## 运行

在项目根目录执行：

```powershell
.\build.ps1 -Run
```

也可以指定 CPLEX 安装目录：

```powershell
.\build.ps1 -CplexHome "你的CPLEX安装目录" -Run
```

入口为 `src/Test/Main.java`。默认运行三个 25 客户算例，每例时间上限为 300 秒。
结果写入 `Results/*_bpc.txt`，包括路径、成本、车辆数和求解状态。`Optimal`
表示已证明最优；达到时间上限时单独记录状态。

## 项目目录

| 路径 | 内容 |
| --- | --- |
| `src/Common` | 实例数据 |
| `src/Model` | 路径、路径池、受限主问题与割 |
| `src/Solver` | 定价、割分离与分支搜索 |
| `src/Util` | 输入输出、数值工具与检查 |
| `src/Test/Main.java` | BPC 运行入口 |
| `Instances` | C101_25、R101_25、RC101_25 算例 |
| `Results` | 求解结果与原模板附带参考值 |

## 默认距离规则下的结果

| 实例 | 总成本 | 车辆数 |
| --- | ---: | ---: |
| C101_25 | 191.8136197787 | 3 |
| R101_25 | 618.3299155463 | 8 |
| RC101_25 | 462.1559467584 | 4 |

采用完整欧氏距离，行驶时间与距离相同。原模板参考值采用每条弧向下截断到一位小数的
另一种距离约定，不能直接与本表比较。

详细类、字段和方法说明见 [README_BPC.md](README_BPC.md)。

## visited 位掩码与计时

Label 的已访问客户集合和割奇偶状态均使用 `int` 位掩码，分别最多支持 32 名客户和 32 条活跃割；仓库不占客户位。默认每个节点仍最多保留 20 条割。
控制台 `time=...s` 为 `Solver.solve()` 的耗时，不含编译、输入读取和结果写入。
修改前后的时间测量见 [对比记录](Results/visited_timing.md)。

三版本同批时间对比见 [label_masks_timing.md](Results/label_masks_timing.md)。

仅修改 visited 与仅修改 cut_parity 的对照见 [四组合消融记录](Results/mask_ablation_timing.md)。
