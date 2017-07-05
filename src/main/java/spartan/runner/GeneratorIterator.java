package spartan.runner;

import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import spartan.runner.Runner.Completion;
import spartan.runner.Runner.Generator;
import spartan.runner.Runner.Iterator;

public final class GeneratorIterator<U> implements Iterator<U> {
  private static final int defaultMaxWorkQueueDepth = 10;
/*
  private static final String clsName = GeneratorIterator.class.getSimpleName();
  private static final AtomicInteger workerThreadNbr = new AtomicInteger(1);
  private static final ExecutorService workerThread = Executors.newCachedThreadPool(r -> {
    final Thread t = new Thread(r);
    t.setDaemon(true);
    t.setName(String.format("%s-pool-thread-#%d", clsName, workerThreadNbr.getAndIncrement()));
    return t;
  });
*/

  private final int maxWorkQueueDepth;
  private final Completion completion;
  private final Consumer<Throwable> exceptionHandler;
  private final ArrayBlockingQueue<U> workQueue;
  private final ArrayList<U> drainedItems;
  private final AtomicBoolean isForkJoinTaskComplete;
  private final ForkJoinTask<Void> future;
  private int drainedItemsCount;
  private int position;
  private U nextValue = null;

  public GeneratorIterator(int maxWorkQueueDepth, Generator<U> theGenerator, Completion completion,
      Consumer<Throwable> exceptionHandler)
  {
    this.maxWorkQueueDepth = maxWorkQueueDepth;
    this.completion = completion;
    this.exceptionHandler = exceptionHandler;
    this.workQueue = new ArrayBlockingQueue<>(maxWorkQueueDepth);
    this.drainedItems = new ArrayList<>(maxWorkQueueDepth + 1);
    this.isForkJoinTaskComplete = new AtomicBoolean(false);
    this.future = ForkJoinPool.commonPool().submit(() -> {
      try {
        theGenerator.call(this.workQueue::put);
      } catch(InterruptedException ex) {
      } finally {
        this.workQueue.done();
      }
      return null; // Void future requires a return value of null
    });
    this.drainedItemsCount = this.position = maxWorkQueueDepth;
  }

  public GeneratorIterator(Generator<U> theGenerator, Completion completion, Consumer<Throwable> exceptionHandler) {
    // default for maximum work queue depth
    this(defaultMaxWorkQueueDepth, theGenerator, completion, exceptionHandler);
  }

  private boolean drainQueue() {
    if (position >= drainedItemsCount) {
      position = 0;
      drainedItems.clear();
      drainedItemsCount = 0;
      boolean isDone = false;
      U pollItem = null;
      for(;;) {
        if (pollItem != null) {
          drainedItems.add(pollItem);
          drainedItemsCount++;
        }
        drainedItemsCount += workQueue.drainTo(drainedItems, maxWorkQueueDepth);
        if (drainedItemsCount > 0) break;
        if (isDone || future.isDone()) return false;
        try {
          pollItem = workQueue.poll(5, TimeUnit.SECONDS);
          isDone = workQueue.isDone();
        } catch (InterruptedException e) {
          future.quietlyComplete();
          return false;
        }
        if (pollItem == null && isDone) return false;
      }
    }
    assert(drainedItemsCount > 0 && position < drainedItemsCount);
    nextValue = drainedItems.get(position++);
    return true;
  }

  private void onDone() {
    if (isForkJoinTaskComplete.compareAndSet(false, true)) { // insure this code block is executed only once
      try {
        future.join();                              // insure that forkJoinTask is in fully completed state
        final Throwable e = future.getException();  // get any exception that may have been thrown on forkJoinTask
        if (e != null) {
          exceptionHandler.accept(e);               // invoke the exception handler lambda on a forkJoinTask exception
        }
      } finally {
        completion.call();                          // invoke the completion lambda (can do cleanup, etc)
      }
    }
  }

  @Override
  public boolean hasNext() {
    if (drainQueue()) {
      return true;
    } else {
      nextValue = null;
      onDone();
      return false;
    }
  }

  @Override
  public U next() { return nextValue; } // will return same value if called multiple times between calling hasNext()

  @Override
  public void cancel() {
    if (!isForkJoinTaskComplete.get()) {
      future.quietlyComplete();
      onDone();
    }
  }
}