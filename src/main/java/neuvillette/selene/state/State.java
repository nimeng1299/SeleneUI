package neuvillette.selene.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 可供多个控件共享的可观察状态，允许 {@code null} 值。
 * 仅供单线程 UI 使用，所有方法和回调均在调用线程执行，不进行线程切换。
 *
 * <p>状态变更立即更新 {@link #get()}，通知按变更顺序排队，并按每次变更时的
 * 订阅顺序执行。回调中再次变更状态时，本轮通知完成后才开始下一轮，不递归通知。
 * 取消的订阅不会再收到尚未开始的回调；新订阅不会收到订阅前已排队的通知。
 *
 * <p>监听器抛出 {@link RuntimeException} 时继续执行其他回调及排队通知，最后
 * 由最外层派发调用抛出首个异常，后续不同实例的异常附加为 suppressed。
 * {@link Error} 立即传播并丢弃未完成的通知，之后仍可继续使用此状态。
 */
public final class State<T> {
    private T value;
    private final List<Observer<T>> observers = new ArrayList<>();
    private final ArrayDeque<Notification<T>> notifications = new ArrayDeque<>();
    private boolean dispatching;

    /** 创建状态；初始值可以是 {@code null}。 */
    public State(T initialValue) {
        value = initialValue;
    }

    /** 返回最新值；派发较早的通知期间也可能已经存在更新的值。 */
    public T get() {
        return value;
    }

    /**
     * 更新状态并通知订阅者。与当前值 {@link Objects#equals(Object, Object) 相等}
     * 时不产生通知。重入调用只排队，监听器异常由最外层派发调用抛出。
     */
    public void set(T value) {
        if (Objects.equals(this.value, value)) {
            return;
        }
        this.value = value;
        notifications.addLast(new Notification<>(value, List.copyOf(observers)));
        dispatch(null);
    }

    /** 根据最新值计算新值，并遵循 {@link #set(Object)} 的相等判断及通知规则。 */
    public void update(UnaryOperator<T> updater) {
        set(Objects.requireNonNull(updater, "updater").apply(value));
    }

    /**
     * 添加订阅并立即同步推送当前值；同一监听器可以独立订阅多次。
     * 首次回调中的状态变更也会排队，避免递归通知。首次回调失败时先撤销本次订阅，
     * 再按通常的异常规则处理其他通知；若本方法未能返回句柄，本次订阅也会撤销。
     * 返回的句柄应在不再使用时取消，以释放监听器引用。
     */
    public Subscription subscribe(Consumer<? super T> observer) {
        Observer<T> subscription = new Observer<>(this, Objects.requireNonNull(observer, "observer"));
        observers.add(subscription);
        boolean completed = false;
        try {
            dispatch(subscription);
            completed = true;
            return subscription;
        } finally {
            if (!completed) {
                subscription.unsubscribe();
            }
        }
    }

    private void dispatch(Observer<T> initial) {
        if (dispatching) {
            if (initial != null) {
                notifyInitial(initial);
            }
            return;
        }

        dispatching = true;
        RuntimeException failure = null;
        try {
            if (initial != null) {
                try {
                    notifyInitial(initial);
                } catch (RuntimeException error) {
                    failure = error;
                }
            }
            while (!notifications.isEmpty()) {
                Notification<T> notification = notifications.removeFirst();
                for (Observer<T> subscription : notification.observers()) {
                    Consumer<? super T> observer = subscription.observer;
                    if (observer != null) {
                        try {
                            observer.accept(notification.value());
                        } catch (RuntimeException error) {
                            if (failure == null) {
                                failure = error;
                            } else if (failure != error) {
                                failure.addSuppressed(error);
                            }
                        }
                    }
                }
            }
        } catch (Error error) {
            notifications.clear();
            throw error;
        } finally {
            dispatching = false;
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void notifyInitial(Observer<T> subscription) {
        try {
            subscription.observer.accept(value);
        } catch (RuntimeException | Error error) {
            subscription.unsubscribe();
            throw error;
        }
    }

    private record Notification<T>(T value, List<Observer<T>> observers) {
    }

    private static final class Observer<T> implements Subscription {
        private State<T> owner;
        private Consumer<? super T> observer;

        private Observer(State<T> owner, Consumer<? super T> observer) {
            this.owner = owner;
            this.observer = observer;
        }

        @Override
        public void unsubscribe() {
            State<T> currentOwner = owner;
            owner = null;
            observer = null;
            if (currentOwner != null) {
                currentOwner.observers.remove(this);
            }
        }
    }
}
