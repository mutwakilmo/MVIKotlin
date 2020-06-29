package com.arkivanov.mvikotlin.extensions.reaktive

import com.arkivanov.mvikotlin.core.annotations.MainThread
import com.arkivanov.mvikotlin.core.store.Bootstrapper
import com.arkivanov.mvikotlin.core.store.Executor
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.utils.internal.initialize
import com.arkivanov.mvikotlin.utils.internal.lateinitAtomicReference
import com.arkivanov.mvikotlin.utils.internal.requireValue
import com.badoo.reaktive.completable.Completable
import com.badoo.reaktive.disposable.Disposable
import com.badoo.reaktive.disposable.scope.DisposableScope
import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.single.Single

/**
 * An abstract implementation of the [Executor] that provides interoperability with Reaktive.
 * Implements [DisposableScope] which disposes when the [Executor] is disposed.
 */
open class ReaktiveExecutor<in Intent : Any, in Action : Any, in State : Any, Result : Any, Label : Any> :
    Executor<Intent, Action, State, Result, Label>,
    DisposableScope {

    private val callbacks = lateinitAtomicReference<Executor.Callbacks<State, Result, Label>>()
    private val getState: () -> State = { callbacks.requireValue.state }
    private val scope = DisposableScope()

    final override fun init(callbacks: Executor.Callbacks<State, Result, Label>) {
        this.callbacks.initialize(callbacks)
    }

    final override fun handleIntent(intent: Intent) {
        executeIntent(intent, getState)
    }

    /**
     * The companion of the [Executor.handleIntent] method
     *
     * @param intent an `Intent` received by the [Store]
     * @param getState a `State` supplier that returns the *current* `State` of the [Store]
     */
    @MainThread
    protected open fun executeIntent(intent: Intent, getState: () -> State) {
    }

    final override fun handleAction(action: Action) {
        executeAction(action, getState)
    }

    /**
     * The companion of the [Executor.handleAction] method
     *
     * @param action an `Action` produced by the [Bootstrapper]
     * @param getState a `State` supplier that returns the *current* `State` of the [Store]
     */
    @MainThread
    protected open fun executeAction(action: Action, getState: () -> State) {
    }

    override fun dispose() {
        scope.dispose()
    }

    /**
     * Dispatches the provided `Result` to the [Reducer].
     * The updated `State` will be available immediately after this method returns.
     *
     * @param result a `Result` to be dispatched to the `Reducer`
     */
    @MainThread
    protected fun dispatch(result: Result) {
        callbacks.requireValue.onResult(result)
    }

    /**
     * Sends the provided `Label` to the [Store] for publication
     *
     * @param label a `Label` to be published
     */
    @MainThread
    protected fun publish(label: Label) {
        callbacks.requireValue.onLabel(label)
    }

    fun Observable<Result>.dispatchResults(isThreadLocal: Boolean = false): Disposable =
        subscribeScoped(isThreadLocal = isThreadLocal, onNext = ::dispatch)

    fun Observable<Label>.publishLabels(isThreadLocal: Boolean = false): Disposable =
        subscribeScoped(isThreadLocal = isThreadLocal, onNext = ::publish)

    fun Single<Result>.dispatchResult(isThreadLocal: Boolean = false): Disposable =
        subscribeScoped(isThreadLocal = isThreadLocal, onSuccess = ::dispatch)

    fun Single<Label>.publishLabel(isThreadLocal: Boolean = false): Disposable =
        subscribeScoped(isThreadLocal = isThreadLocal, onSuccess = ::publish)

    fun Maybe<Result>.dispatchResult(isThreadLocal: Boolean = false): Disposable =
        subscribeScoped(isThreadLocal = isThreadLocal, onSuccess = ::dispatch)

    fun Maybe<Label>.publishLabel(isThreadLocal: Boolean = false): Disposable =
        subscribeScoped(isThreadLocal = isThreadLocal, onSuccess = ::publish)

    //region DisposableScope delegate

    final override val isDisposed: Boolean get() = scope.isDisposed

    final override fun <T : Disposable> T.scope(): T = scope.run { this@scope.scope() }

    final override fun <T> T.scope(onDispose: (T) -> Unit): T = scope.run { this@scope.scope(onDispose) }

    final override fun Completable.subscribeScoped(
        isThreadLocal: Boolean,
        onSubscribe: ((Disposable) -> Unit)?,
        onError: ((Throwable) -> Unit)?,
        onComplete: (() -> Unit)?
    ): Disposable =
        scope.run {
            this@subscribeScoped.subscribeScoped(
                isThreadLocal = isThreadLocal,
                onSubscribe = onSubscribe,
                onError = onError,
                onComplete = onComplete
            )
        }

    final override fun <T> Maybe<T>.subscribeScoped(
        isThreadLocal: Boolean,
        onSubscribe: ((Disposable) -> Unit)?,
        onError: ((Throwable) -> Unit)?,
        onComplete: (() -> Unit)?,
        onSuccess: ((T) -> Unit)?
    ): Disposable =
        scope.run {
            this@subscribeScoped.subscribeScoped(
                isThreadLocal = isThreadLocal,
                onSubscribe = onSubscribe,
                onError = onError,
                onComplete = onComplete,
                onSuccess = onSuccess
            )
        }

    final override fun <T> Observable<T>.subscribeScoped(
        isThreadLocal: Boolean,
        onSubscribe: ((Disposable) -> Unit)?,
        onError: ((Throwable) -> Unit)?,
        onComplete: (() -> Unit)?,
        onNext: ((T) -> Unit)?
    ): Disposable =
        scope.run {
            this@subscribeScoped.subscribeScoped(
                isThreadLocal = isThreadLocal,
                onSubscribe = onSubscribe,
                onError = onError,
                onComplete = onComplete,
                onNext = onNext
            )
        }

    final override fun <T> Single<T>.subscribeScoped(
        isThreadLocal: Boolean,
        onSubscribe: ((Disposable) -> Unit)?,
        onError: ((Throwable) -> Unit)?,
        onSuccess: ((T) -> Unit)?
    ): Disposable =
        scope.run {
            this@subscribeScoped.subscribeScoped(
                isThreadLocal = isThreadLocal,
                onSubscribe = onSubscribe,
                onError = onError,
                onSuccess = onSuccess
            )
        }

    //endregion
}
