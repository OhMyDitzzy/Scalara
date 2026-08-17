package id.ditzzy.scalara.main;

/**
 * Wraps a value that should be handled at most once by its observer — e.g.
 * "show this Snackbar" — so it doesn't re-fire on configuration changes or
 * when a new observer attaches to an already-set {@code LiveData}.
 *
 * <p>{@code MainViewModel} uses this for transient UI feedback (success/error
 * messages) as opposed to durable state like the preset list, which should
 * always re-deliver its current value to a new observer.
 */
public final class ConsumableEvent<T> {

    private final T content;
    private boolean consumed = false;

    public ConsumableEvent(T content) {
        this.content = content;
    }

    /**
     * Returns the wrapped content the first time this is called, and
     * {@code null} on every call after that.
     */
    public T consume() {
        if (consumed) {
            return null;
        }
        consumed = true;
        return content;
    }
}
