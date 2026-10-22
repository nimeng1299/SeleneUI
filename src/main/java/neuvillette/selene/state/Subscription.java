package neuvillette.selene.state;

/** 状态订阅的取消句柄；重复取消安全，不会影响其他订阅。 */
public interface Subscription extends AutoCloseable {
    /** 取消订阅并释放监听器引用。 */
    void unsubscribe();

    /** 与 {@link #unsubscribe()} 等价，不抛出受检异常。 */
    @Override
    default void close() {
        unsubscribe();
    }
}
