# Layout 布局控件

参考 [Ratatui Layout](https://ratatui.rs/concepts/layout/) 的方向、约束和 Flex 概念。
`Layout` 继承 `BaseWidget`，自动分配直接子控件的位置和大小，并沿用父控件的绘制与输入分发。
只将根控件注册到 `Screen`。运行游戏内的 `/selene` 可查看随窗口大小调整的嵌套布局示例。

```java
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.layout.Direction;
import neuvillette.selene.layout.Flex;
import neuvillette.selene.widget.Layout;

Layout root = new Layout(20, 20, 400, 240)
        .Direction(Direction.VERTICAL)
        .Padding(12)
        .Spacing(8);

root.addChild(header, Constraint.length(32));
Layout body = root.addChild(new Layout()
        .Direction(Direction.HORIZONTAL)
        .Spacing(8), Constraint.fill(1));
body.addChild(sidebar, Constraint.percentage(25));
body.addChild(content, Constraint.fill(1));
root.addChild(footer, Constraint.length(24));

addRenderableWidget(root);
```

`header`、`sidebar`、`content`、`footer` 可以是任何 `BaseWidget`，包括 `Block` 或另一个 `Layout`。
它们原有的位置、宽高会被布局覆盖。`Layout` 自身没有背景，需要背景时可放入 `Block`。
padding、spacing 等空白区域不拦截鼠标点击、悬停或滚轮事件。

## 配置

- 默认竖向 `Direction.VERTICAL`、起点对齐 `Flex.START`、零间距、零 padding。
- `Direction.HORIZONTAL` 水平分配宽度，所有子控件的高度填满内容区域；竖向时交换两个轴。
- `Padding(all)`、`Padding(vertical, horizontal)`、`Padding(top, right, bottom, left)` 沿用原有内边距规则。
- `Spacing(value)` 设置相邻子控件间距，必须非负。空间不足以容纳间距时先缩小间距，段尺寸最小为零。
- `addChild(child, constraint)` 添加子控件并设置约束；普通 `addChild(child)` 或带 parent 的构造方法默认使用 `fill(1)`。
- `setConstraint(child, constraint)` 更新单个直接子控件的约束。
- `Constraints(...)` / `setConstraints(...)` 按添加顺序设置**现有**子控件，数量必须匹配。
- `getConstraint(child)` 获取当前约束；移除或换绑后，原容器不会保留该子控件的约束。

Fluent 方法也有对应的 `setDirection`、`setFlex`、`setSpacing`、`setPadding`。
几何查询、绘制、鼠标命中和焦点导航前会自动刷新布局；也可用 `root.layout()` 显式刷新。
隐藏的子控件仍占据布局位置，若要释放空间，应调用 `removeChild`。

## 尺寸约束

| 方法 | 含义 |
| --- | --- |
| `Constraint.length(32)` | 主轴长度目标为 32 个 GUI 单位 |
| `Constraint.percentage(25)` | 父内容区域主轴长度的 25%，取值 0–100 |
| `Constraint.ratio(1, 3)` | 父内容区域主轴长度的 1/3，分母必须大于零 |
| `Constraint.min(20)` | 优先保留至少 20，且没有正权 Fill 时参与剩余空间扩张 |
| `Constraint.max(80)` | 最多 80；在明确尺寸目标分配后，从余量中尽量取得该大小 |
| `Constraint.fill(2)` | 按权重 2 分配剩余空间；`fill(0)` 不参与扩张 |

尺寸和权重必须非负。Percentage / Ratio 的基数是**整个父内容区域**，包括计划放置间距的空间，
不是扣除 Length 之后的剩余长度。需要按剩余空间比例划分时，使用 Fill 或嵌套 Layout。

实现采用确定性分配器，不使用 Ratatui 的 Cassowary 求解器：先预留间距，再依次满足 Min 下限、
Length、Percentage、Ratio，之后分配 Max 目标。Max 始终不超过上界。有正权 Fill 时按权重分配
余量；否则 Min 均分余量。最后的空白由 Flex 处理。

当空间不足时，同级请求按比例压缩，高优先级先保留；即使 Min 也可能因父区域过小而缩小。
Percentage / Ratio 的同组目标保留小数，将总量四舍五入后再按最大余数法分配，平局时靠前的子控件优先。
结果保证非负、不重叠且不越过内容区域。冲突时的具体结果不承诺与 Ratatui 完全一致。

## Flex 对齐

当约束没有用完主轴空间时：

| 值 | 行为 |
| --- | --- |
| `Flex.START` | 靠起点排列，空白放末尾 |
| `Flex.END` | 靠终点排列，空白放开头 |
| `Flex.CENTER` | 整组居中 |
| `Flex.SPACE_BETWEEN` | 剩余空白放在子控件之间 |
| `Flex.SPACE_AROUND` | 两侧半份、子控件之间一份空白 |
| `Flex.LEGACY` | 将余量分给最后可扩张的子控件，仍遵守 Max 上界及 Fill(0) |

例如，在宽 80 的水平布局中放两个 `length(20)`，`Flex.CENTER` 会在左右各留下 20。
设置 `Spacing(4)` 后，左右各留下 18，两个子控件之间间隔 4。

## 填满屏幕

```java
Minecraft.getInstance().setScreen(new SeleneScreen(title, root, true));
```

第三个参数 `true` 会在打开界面和窗口尺寸变化时让根控件填满 GUI 区域。
原有的两个参数构造方法保留传入控件的坐标和尺寸。

运行 `./gradlew build` 可编译并执行布局回归测试。
