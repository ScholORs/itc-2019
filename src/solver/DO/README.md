# Windows HC training-data collection

这个目录是批量采集、核验、去重和编码的入口。使用现有 Java DF 与 HC 的移动规则，不调用第三方算法，不使用 restart，不训练模型。旧的 `DO/Test/code` 和已有实验记录保留，用于历史复现。

## 在 Windows 上运行

1. 在 Windows 电脑上通过 GitHub clone/pull 同步项目，保持 `itc-2019/src` 目录结构。运行脚本会直接使用仓库中的实例和 Java 源码，无需压缩包。
2. 安装 **JDK 17 或更新版本**，确保命令行能运行 `java -version` 和 `javac -version`。
3. 安装 **Python 3.9 或更新版本**。脚本优先使用 `py -3`，否则使用 PATH 中的 `python`。只用标准库，不需要安装 NumPy、PyTorch 或其他 Python 包。
4. 双击 `src\solver\DO\run_windows.bat`。它会编译 Java 并执行采集。

默认配置：12 个并发任务，每轮 120 秒，目标 2500 个不同 seed，整批最长 8 小时。每个任务是独立 JVM，堆上限 512 MB；12 个任务的堆上限合计 6 GB，实际系统还需要 JVM 和 Python 等额外内存。种子起点由启动时间生成，具体种子写入配置和结果。

实例固定为 `src\Half2\muni-fsps-spr17_postcompetition2.xml`。结果固定默认保存到 `src\solution\muni-fsps-spr17\random_hc_日期_时间\`，每次运行独立子目录，不覆盖历史文件。该实例不同于此前 DO/Test 原始实例实验，编码和训练数据不可直接混用。

也可在命令行调整参数：

```bat
cd /d "D:\your-project\itc-2019\src\solver\DO"
run_windows.bat --workers 12 --runs 2500 --seconds 120 --hours 8
```

首次测试建议先执行：

```bat
run_windows.bat --workers 4 --runs 4 --seconds 10 --hours 0.1
```

## 手动停止：保留已完成结果，丢弃进行中的解

在采集窗口按 **Ctrl+C**，或者双击 **stop_windows.bat**。后者会向当前批次写入 STOP 文件。程序每 0.25 秒检查停止请求，终止正在运行的 Java 子进程，不再启动新任务，清理未完成任务，然后从已经完成的结果生成数据集与停止汇总。等待采集窗口显示最终 summary 后再关闭窗口。

`completed\seed_...` 是正式结果目录。只有搜索到时间（或 DF=0）、保存最终 XML、重算 DF、核验编码并退出 JVM 后，才原子地提交到此目录。手动停止时没有提交的任务均丢弃，包括刚结束搜索但尚未完成核验的任务。中间解不会提交。

不要用直接断电、任务管理器强制结束 Python 或关闭终端窗口代替上述停止方式：这些方式可能不给程序清理机会。残留 `.pending` 无论如何都不会作为训练数据。若发生硬中断，保留 completed 文件夹，用下面命令重建数据集；确认没有采集进程运行后，可删除 DO 目录中的 `.collector.lock` 和 `active_run.json`，再启动新批次。

```bat
py -3 collect.py --export-only "D:\your-project\itc-2019\src\solution\muni-fsps-spr17\random_hc_日期_时间"
```

## 搜索和记录

每个 seed 从随机 timetable 开始，仅优化 DF。随机选择一个 class，再随机替换 time 或 room，候选 DF 不高于当前 DF 即接受。无 restart，无 NFE 上限，DF=0 提前结束。每个 seed 仅保存一个终点 XML，不因 DF 较高而排除。

主程序读取本批实例快照一次，每个任务在自己的 `.pending/seed_.../instance.xml` 使用独立副本，避免多个 JVM 打开同一个实例路径。该副本在 JVM 退出后删除，不进入训练集。XML 写入/核验均显式关闭文件流；日志只在 JVM 退出后复制。JSON 更新、目录提交与清理对 Windows 临时文件占用做有限重试，任务失败不再导致整批任务一起退出；错误中记录 WinError、文件路径和完整 traceback。

时间由 Java `System.nanoTime` 从随机初始化前计起，包含通信和记录，不含 JVM 启动和实例读取；候选评价不能中途打断，所以会略超 120 秒。30、60、90、120 秒仅记录 DF/NFE/实际时间，不保存中间 XML。初始解计 1 NFE，每个 HC 候选（包括没有改变的候选）都计 1 NFE。

每轮 `improvements.csv` 仅记录初始解和严格 DF 下降事件，避免几千轮逐评价日志占用大量磁盘。完整 NFE 和时间 checkpoint 写入 summary。并发资源竞争会影响 NFE，不宜将并发吞吐量与过去单任务运行直接比较。

整批墙钟预算从启动开始，包含编译/加载；剩余时间不足一轮时不再启动任务，到整批截止时间仍未提交的任务也丢弃。最终去重和编码可能在 8 小时搜索截止后继续片刻。

## 数据清理与编码

从本批 `completed` 读取所有终点解，不扫描或导入历史第三方结果。精确重复按所有 time/room 类别索引判断；近重复按非固定分类块的 Hamming 距离判断，阈值为 `floor(可变块数 × 0.01)`。优先保留 DF 较低者；平局保留 seed 较小者。原始完成结果不删除，仅记录数据集排除清单。这个 1% 阈值是当前筛选配置，可以通过 `--near-fraction` 调整，设为 0 则仅做精确去重。

编码从本次指定 XML 自动生成：class 按 ID 升序，每个 class 先 time 块、后 room 块（无需 room 的 class 无 room 块）。time 类别按 days/weeks/start 排序，room 按 ID 排序，类别索引从 0 开始。每个分类块中恰有一个 one-hot 位为 1。DF/seed 是元数据，不是模型输入。每个最终 XML 都与编码逐块对照，并由 Java 重新计算 DF。

`dataset\metadata.csv` 行顺序与 `choices.csv` / `encoded.npz` 一致。seed 的 SHA-256 哈希固定划分约 80% train、20% validation；比例不是严格等分。之后的 DO+HC 搜索效果测试应使用独立的新 seed。

输出结构：

```text
random_hc_日期_时间/
  config.json                  参数、seed 起点、版本、哈希
  instance.xml                 本批指定实例的快照
  encoding_schema.json         本批编码域和顺序
  compile.log
  progress.json
  summary.json                 完成/丢弃数量、停止原因、DF分布、NFE
    sources/                     运行代码快照
  completed/seed_.../
    final.xml                  唯一保存解
    summary.json               seed、DF、NFE、时间、checkpoint、编码与核验
    improvements.csv
    java_stderr.log
  .pending/                    正常结束或手动停止后为空，不属于训练数据
  errors/                      失败任务记录（不保留该任务解）
  dataset/
    encoding_schema.json
    metadata.csv
    choices.csv
    deduplication.json
    encoded.npz                choices(int32)、onehot(uint8)、df/int64、seeds/int64
```

采集端无需 NumPy，但后续训练可直接用 `numpy.load("encoded.npz")`。onehot 为 uint8，需要浮点输入时再转换；本脚本不会自动启动深度学习训练。
