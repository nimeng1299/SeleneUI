# 共享状态

`State<T>` 保存一份数据。需要共享的控件持有同一个实例，无需有相同的 parent，
也无需向 Screen 单独注册。运行 `/selene state`，点击任意面板可看到两个面板同步计数。

在组件构造器中调用继承自 `BaseWidget` 的 `subscribe(state, listener)`，由组件声明自己需要的数据。
一个组件可以订阅多个状态，多个组件也可以订阅同一份状态：

```java
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.state.State;
import neuvillette.selene.widget.Block;

class CounterPanel extends Block {
    private final State<Integer> count;

    CounterPanel(State<Integer> count, State<Color> accent, String label) {
        super(0, 0, 0, 0, Component.empty());
        this.count = count;
        subscribe(count, value -> setMessage(Component.literal(label + ": " + value)));
        subscribe(accent, this::FillColor);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        count.update(value -> value + 1);
    }

    @Override
    protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderSelf(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + getHeight() / 2 - 4, 0xFFFFFFFF);
    }
}
```

组装界面时传入共享实例，`Screen` 无需逐条管理组件订阅：

```java
import neuvillette.selene.screen.SeleneScreen;
import neuvillette.selene.widget.Layout;

State<Integer> count = new State<>(0);
State<Color> accent = new State<>(Color.fromRGB(40, 100, 180));
Layout root = new Layout().Horizontal().Padding(16).Spacing(8);
root.addChild(new CounterPanel(count, accent, "数量"));
root.addChild(new CounterPanel(count, accent, "总计"));
Minecraft.getInstance().setScreen(new SeleneScreen(Component.literal("Shared state"), root, true));

// 两个面板都会更新：
count.set(10);
accent.set(Color.fromRGB(35, 130, 130));
```

构造器声明的订阅在控件尚未挂载时不通知。`SeleneScreen` 挂载控件树后，每条订阅会同步当前值，
再接收后续更新。文字由组件自己的 `renderSelf()` 绘制；普通 `Block` 只绘制背景。

## API 与更新规则

| 方法 | 行为 |
| --- | --- |
| `new State<>(initialValue)` | 创建状态，允许值为 `null` |
| `get()` | 读取当前值 |
| `set(value)` | 更新值，`Objects.equals` 判断相等时不通知 |
| `update(updater)` | 用当前值计算并设置新值，例如 `count.update(n -> n + 1)` |
| `State.subscribe(listener)` | 立即提供当前值，之后订阅变化，由调用方手动取消 |
| `BaseWidget.subscribe(state, listener)` | 在组件内部声明随挂载状态启停的订阅，挂载时同步当前值 |
| `Subscription.unsubscribe()` / `close()` | 永久取消这一条订阅，不影响其他订阅；重复调用安全 |

监听器按订阅顺序调用。回调中的 `set()` 会立即改变 `get()` 的结果，通知则排队到当前轮之后，
避免递归通知和新值被旧通知覆盖。回调参数是该次变更的值，可能与重入更新后的 `get()` 不同。
新订阅只接收当前值及之后的变更；已取消的订阅即使仍在某次通知快照中，也不会继续调用。

某个监听器抛出 `RuntimeException` 时，其他监听器和已排队的变更仍会处理；
最后向调用者抛出第一个异常，其他异常附在 suppressed 中。`State.subscribe()` 首次通知失败会撤销该订阅。
`Error` 会立即传播，清除未处理通知并恢复派发状态。

## 控件与界面的生命周期

`BaseWidget.subscribe(state, listener)` 是供组件子类使用的 `protected` 方法。
`SeleneScreen` 自动挂载和卸载整棵控件树：打开时开始订阅，关闭时暂停，重新打开时读取最新值并恢复。
窗口 resize 不会重复注册订阅。
若挂载时的首次同步失败，本次挂载的子树会卸载，保留绑定声明，可在修复原因后再次 `mount()`。

`removeChild()` 会卸载移出的子树，自动暂停这些组件订阅；再次加入已挂载的父控件时恢复。
临时卸载会释放状态对监听器的引用，但组件保留订阅声明以便重新使用。隐藏或禁用控件不等于卸载。
在两个已挂载的父控件之间直接换绑会保持订阅；换到未挂载的父控件时暂停。

组件内的 `subscribe` 也返回 `Subscription`。一般无需保存；若某条绑定确定不再需要，
可调用其 `unsubscribe()` 或 `close()` 永久取消，之后重新挂载也不会恢复它。

使用普通自定义 `Screen` 时，在 `init()` 中注册根控件并调用 `root.mount()`，
在 `removed()` 中调用 `root.unmount()`。重复挂载不会重复订阅：

```java
@Override
protected void init() {
    super.init();
    addRenderableWidget(root);
    root.mount();
}

@Override
public void removed() {
    root.unmount();
    super.removed();
}
```

直接调用 `state.subscribe(listener)` 仍采用显式生命周期：立即提供当前值，
`removeChild()`、换绑 parent 或关闭 Screen 都不会自动暂停它，需要调用方保存并取消句柄：

```java
import neuvillette.selene.state.Subscription;

Subscription binding = count.subscribe(value -> label.setMessage(Component.literal("数量：" + value)));
// 调用方不再需要监听时：
binding.unsubscribe(); // 或 binding.close()
```

若数据影响布局，在监听器中调用 `setSize()`、`setPadding()` 或 `Layout.setConstraint()` 等已有方法，
它们会使布局失效；下次查询、绘制或命中测试会自动重新计算。只影响绘制的数据也可以在
`renderSelf()` 中直接读取 `state.get()`，无需订阅。

## 使用边界

`State` 面向单线程 UI，不自动切换线程。与控件绑定的状态应在 Minecraft 客户端线程访问；
后台任务的结果可通过 `Minecraft.getInstance().execute(() -> state.set(result))` 提交。

集合和对象建议作为不可变值使用：先创建新值，再 `set()`。直接修改 `get()` 返回对象的内部字段
不会触发通知，同一对象再传给 `set()` 也可能被相等判断忽略。

`./gradlew build` 会执行共享状态及控件集成回归测试。
