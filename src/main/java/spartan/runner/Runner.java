package spartan.runner;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class Runner<T, U> {
  private static final int defaultMaxWorkQueueDepth = 10;
  private static final String invalidInitializationErrMsgFmt = String.format("invalid initialization of %s object: %%s",
      Runner.class.getSimpleName());
  private static final String generatorInitErr = "generator lambda not set";
  private static final String completionInitErr = "completion lambda not set";
  private static final String exceptionHandlerInitErr = "exception handler lambda not set";

  private final Generator<U> generator;
  private final GeneratorWithStartValue<T, U> generatorWithStartValue;
  private Completion completion = null;
  private Consumer<Throwable> exceptionHandler = null;
  private int maxWorkQueueDepth = defaultMaxWorkQueueDepth;

  private Runner(Generator<U> generator, GeneratorWithStartValue<T, U> generatorWithStartValue) {
    this.generator = generator;
    this.generatorWithStartValue = generatorWithStartValue;
  }
  private Runner(Generator<U> generator) { this(generator, null); }
  private Runner(GeneratorWithStartValue<T, U> generator) { this(null, generator); }

  public interface Iterator<T> extends java.util.Iterator<T> {
    void cancel();
  }

  @FunctionalInterface
  public interface Yield<U> {
    void accept(U value) throws InterruptedException;
  }

  @FunctionalInterface
  public interface Generator<U> {
    void call(Yield<U> yield) throws InterruptedException;
  }

  @FunctionalInterface
  public interface GeneratorWithStartValue<T, U> {
    void call(T seedValue, Yield<U> yield) throws InterruptedException;
  }

  @FunctionalInterface
  public interface Completion {
    void call();
  }

  @FunctionalInterface
  public interface Then<U> {
    ElseHandleException<U> then(Completion completion);
  }

  @SuppressWarnings("serial")
  public static final class InvalidInitializationException extends RuntimeException {
    public InvalidInitializationException(String message) { super(message); }
    public InvalidInitializationException(String message, Throwable cause) { super(message, cause); }
  }

  @FunctionalInterface
  public interface ReturnIterator<U> {
    Iterator<U> iterator() throws InvalidInitializationException;
  }

  @FunctionalInterface
  public interface ElseHandleException<U> {
    ReturnIterator<U> elseHandleException(Consumer<Throwable> exceptionHandler);
  }

  private static <T, U> Then<U> makeThen(final Runner<T, U> runner, final T seedValue) {
    return completion -> {
      runner.completion = completion;
      return exceptionHandler -> {
        runner.exceptionHandler = exceptionHandler;
        return () -> runner.getConsumerIterator(seedValue);
      };
    };
  }

  public static <U> Then<U> run(int maxWorkQueueDepth, Generator<U> generator) {
    final Runner<Void, U> runner = new Runner<>(generator);
    runner.maxWorkQueueDepth = maxWorkQueueDepth;
    return makeThen(runner, null);
  }

  public static <U> Then<U> run(Generator<U> generator) {
    return run(defaultMaxWorkQueueDepth, generator);
  }

  public static <T, U> Then<U> run(int maxWorkQueueDepth, T seedValue, GeneratorWithStartValue<T, U> generator) {
    final Runner<T, U> runner = new Runner<>(generator);
    runner.maxWorkQueueDepth = maxWorkQueueDepth;
    return makeThen(runner, seedValue);
  }

  public static <T, U> Then<U> run(T seedValue, GeneratorWithStartValue<T, U> generator) {
    return run(defaultMaxWorkQueueDepth, seedValue, generator);
  }

  private Generator<U> theGenerator(final T seedValue) throws InvalidInitializationException {
    if (generatorWithStartValue != null) {
      return yieldLambda -> generatorWithStartValue.call(seedValue, yieldLambda);
    } else if (generator != null) {
      return generator;
    } else {
      String errmsg = String.format(invalidInitializationErrMsgFmt, generatorInitErr);
      throw new InvalidInitializationException(errmsg);
    }
  }

  private Iterator<U> getConsumerIterator(final T seedValue) throws InvalidInitializationException {
    final Generator<U> theGenerator = theGenerator(seedValue);
    if (completion == null) {
      String errmsg = String.format(invalidInitializationErrMsgFmt, completionInitErr);
      throw new InvalidInitializationException(errmsg);
    } else if (exceptionHandler == null) {
      String errmsg = String.format(invalidInitializationErrMsgFmt, exceptionHandlerInitErr);
      throw new InvalidInitializationException(errmsg);
    }
    return new GeneratorIterator<U>(maxWorkQueueDepth, theGenerator, completion, exceptionHandler);
  }

  public static <U> Stream<U> stream(Iterator<U> iterator) {
    return StreamSupport
        .stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.IMMUTABLE), false);
  }
}