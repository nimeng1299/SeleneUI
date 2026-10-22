package neuvillette.selene.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import neuvillette.selene.state.State;
import neuvillette.selene.state.Subscription;

/**
 * 控件的状态订阅集合，仅供单线程 UI 使用。
 * 未挂载时只保存绑定定义；挂载时立即同步最新状态，卸载时停止监听但保留定义。
 * 返回的取消句柄会永久删除对应绑定，之后重新挂载也不会恢复。
 */
final class WidgetSubscriptions {
    private final List<Binding<?>> bindings = new ArrayList<>();
    private boolean mounted;
    private long generation;

    boolean isMounted() {
        return mounted;
    }

    /** 已挂载时立即激活，否则等到下一次挂载；每次调用产生独立绑定。 */
    <T> Subscription subscribe(State<T> state, Consumer<? super T> observer) {
        Binding<T> binding = new Binding<>(this,
                Objects.requireNonNull(state, "state"), Objects.requireNonNull(observer, "observer"));
        bindings.add(binding);
        try {
            binding.connect();
            return binding;
        } catch (RuntimeException | Error error) {
            // 没有返回句柄的绑定不能继续保留，否则调用方无法取消。
            binding.unsubscribe();
            throw error;
        }
    }

    /**
     * 激活全部绑定；重复调用无效。
     * 任意首次同步回调失败都会回滚到未挂载状态，绑定定义保留以便再次挂载。
     */
    void mount() {
        if (mounted) {
            return;
        }
        mounted = true;
        generation++;
        try {
            for (Binding<?> binding : List.copyOf(bindings)) {
                if (!mounted) {
                    break;
                }
                binding.connect();
            }
        } catch (RuntimeException | Error error) {
            unmount();
            throw error;
        }
    }

    /** 停止全部监听，保留绑定定义；重复调用无效。 */
    void unmount() {
        if (!mounted) {
            return;
        }
        mounted = false;
        generation++;
        // 先失效整个挂载轮次，首次同步回调中尚未返回句柄的连接也立即停止通知。
        for (Binding<?> binding : List.copyOf(bindings)) {
            binding.disconnect();
        }
    }

    private static final class Binding<T> implements Subscription {
        private WidgetSubscriptions owner;
        private State<T> state;
        private Consumer<? super T> observer;
        private Subscription active;
        private boolean connecting;

        private Binding(WidgetSubscriptions owner, State<T> state, Consumer<? super T> observer) {
            this.owner = owner;
            this.state = state;
            this.observer = observer;
        }

        private void connect() {
            if (connecting) {
                return;
            }
            while (owner != null && owner.mounted && active == null) {
                WidgetSubscriptions currentOwner = owner;
                long currentGeneration = currentOwner.generation;
                State<T> currentState = state;
                Subscription connected;
                connecting = true;
                try {
                    // 只捕获绑定和轮次，不把 observer 永久保存在底层回调中。
                    connected = currentState.subscribe(value -> notifyIfCurrent(currentGeneration, value));
                } finally {
                    connecting = false;
                }

                if (owner == currentOwner && currentOwner.mounted
                        && currentOwner.generation == currentGeneration) {
                    active = connected;
                } else {
                    // 首次同步回调可能已卸载或永久取消，此时才拿到的旧句柄也必须取消。
                    connected.unsubscribe();
                }
                // 若回调中先卸载再挂载，重入的 mount 会跳过正在连接的绑定；
                // 旧句柄处理完毕后在这里补连最新轮次，避免递归连接同一个绑定。
            }
        }

        private void notifyIfCurrent(long expectedGeneration, T value) {
            WidgetSubscriptions currentOwner = owner;
            if (currentOwner != null && currentOwner.mounted
                    && currentOwner.generation == expectedGeneration) {
                Consumer<? super T> currentObserver = observer;
                if (currentObserver != null) {
                    currentObserver.accept(value);
                }
            }
        }

        private void disconnect() {
            Subscription subscription = active;
            active = null;
            if (subscription != null) {
                subscription.unsubscribe();
            }
        }

        @Override
        public void unsubscribe() {
            WidgetSubscriptions currentOwner = owner;
            owner = null;
            state = null;
            observer = null;
            if (currentOwner != null) {
                currentOwner.bindings.remove(this);
            }
            disconnect();
        }
    }
}
