import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import spartan.runner.Runner;
import spartan.runner.Runner.Iterator;

public class mainRun {
  private static final long programStartTime = System.currentTimeMillis();
  private static final String clsName = mainRun.class.getSimpleName();

  public static void main(String[] args) throws IOException {
    if (args.length <= 0) {
      usage(System.out);
      return;
    }

    System.out.printf("%s: Generate Fibonacci Sequence values%n", clsName);

    final double ceiling = args.length > 0 ? Double.parseDouble(args[0]) : 30 /* default */;
    final String op = args.length > 1 ? args[1] : "stream-all" /* default */;
    final AtomicInteger yieldCallCount = new AtomicInteger();
    final long calculationStartTime = System.currentTimeMillis();

    final Iterator<Double> iter = Runner.<Double, Double>
      run(ceiling, (seedValue, yield) -> {
        System.out.printf("DEBUG: generator lambda invoked for max ceiling value of: %s%n", seedValue);
        int count = 0;
        final double maxCeiling = seedValue;
        double j = 0, i = 1;
        yield.accept(j);
        count++;
        if (maxCeiling <= j) return;
        yield.accept(i);
        count++;
        if (maxCeiling == i) return;
        for(;;) {
          double tmp = i;
          i += j;
          j = tmp;
          if (i > maxCeiling) break;
          yield.accept(i);
          count++;
        }
        yieldCallCount.set(count);
      })
      .then(() -> {
        System.out.println("DEBUG: completion lambda invoked - generator is done (or canceled)");
      })
      .elseHandleException(ex -> {
        System.out.println("DEBUG: exception handler lambda invoked for generator exception:");
        ex.printStackTrace(System.err);
      })
      .iterator();

    @SuppressWarnings("unchecked")
    List<Double> results = Collections.EMPTY_LIST;

    final Predicate<Double> filterForSubset = d -> {
      if (d > 10000d && d < 1000000000d) {
        return true;
      }
      if (d > 1000000000d) {
        iter.cancel();
      }
      return false;
    };

    // Here is where the generator expressed above is consumed. The first
    // two cases wrap the returned iterator in a Java 8 stream. The first
    // case consumes all that the generator yields up until the specified
    // ceiling value; the second case consumes a subset and cancels the
    // iterator. The remaining two cases consume via the iterator itself as
    // returned by the Runner helper class. The first iterator case iterates
    // through all generator yielded values and the second iterator case
    // consumes a subset and cancels. Note that both the stream case and
    // the iterator case use the same predicate lambda to filter a subset.
    switch (op.toLowerCase()) {
    case "stream-all":
      results = Runner.stream(iter).collect(Collectors.toList());
      break;
    case "stream-cancel":
      results = Runner.stream(iter)
        .filter(filterForSubset)
        .collect(Collectors.toList());
      break;
    case "iter-all":
      results = new ArrayList<>(300);
      while(iter.hasNext()) {
        results.add(iter.next());
      }
      break;
    case "iter-cancel":
      results = new ArrayList<>(50);
      while(iter.hasNext()) {
        final Double d = iter.next();
        if (filterForSubset.test(d)) {
          results.add(d);
        }
      }
      break;
    default:
      System.err.printf("ERROR: operation \"%s\" is unknown%n", op);
      usage(System.err);
      System.exit(1);
    }

    final Long calcDiffTime = Long.valueOf(System.currentTimeMillis() - calculationStartTime);

    results.forEach(System.out::println);
    System.out.printf("%d values returned%n", results.size());
    System.out.printf("%d yield() call count%n", yieldCallCount.get());

    System.out.printf("%s: Generate Fibonacci Sequence runtime duration (milliseconds): %.0f%n",
        clsName, calcDiffTime.doubleValue());

    final Long totalDiffTime = Long.valueOf(System.currentTimeMillis() - programStartTime);
    System.out.printf("%s: Program total runtime duration (seconds): %.3f%n",
        clsName, totalDiffTime.doubleValue() / 1000d);
  }

  private static void usage(java.io.PrintStream out) {
    out.printf("Usage: %s {n} [{operation}]%n{n} : max ceiling number%n{operation} : one of following:%n", clsName);
    out.printf("    stream-all    - uses Java 8 stream to capture all generated values (default)%n");
    out.printf("    stream-cancel - uses Java 8 stream to capture subset and then cancel the generator%n");
    out.printf("    iter-all      - uses Java iterator to capture all generated values%n");
    out.printf("    iter-cancel   - uses Java iterator to capture subset and then cancel the generator%n");
  }
}