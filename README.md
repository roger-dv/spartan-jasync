# spartan-jasync
JavaScript-like generators (as introduced in ECMA5) for the Java programming language (as a library)

## Generator pattern for Java

The JavaScript language, in ECMA5 standard, introduced generator functions. These look somewhat like normal functions but their implementation will use `yield` keyword to pass generated values to a consumer. Here is a very simple generator function:

```JavaScript
function *foo() {
  yield 1;
  yield 2;
  yield 3;
}
```

The new special syntax of the `*` asterisk character preceding the function name (indicates this function returns an iterator when invoked) and then the use of `yield` keyword for supplying *generated* values to a consumer of the returned iterator, is the essence of what constitutes a generator function.

Here is an example that executes in the JavaScript engine of nodejs v6.11.x:

```JavaScript
// nodejs v6.11.x

function *foo() {
  yield 1;
  yield 2;
  yield 3;
  yield "hello";
  yield "world";
}

console.log("generator *foo() values (explicit iterator):");
var iter = foo();
console.log(iter.next());
console.log(iter.next());
console.log(iter.next());
console.log(iter.next());
console.log(iter.next());

console.log("\ngenerator *foo() values (for loop):");
for(var v of foo()) {
  console.log(v);
}
```
The output will look like so:
```
generator *foo() values (explicit iterator):
{ value: 1, done: false }
{ value: 2, done: false }
{ value: 3, done: false }
{ value: 'hello', done: false }
{ value: 'world', done: false }

generator *foo() values (for loop):
1
2
3
hello
world
```

Very interesting, yes?

JavaScript has a `for` loop that makes it very convenient to consume from an iterator object until it is exhausted. As one may surmise, there is some special magic going on in the JavaScript runtime to enable this manner of executing a function. The code consuming the iterator is essentially surrending execution to the generator code each time `next()` is invoked on the iterator object; the generator code proceeds to the first use of `yield` and passes a value and execution context back to the iterator consumer. This proceeds back and forth until the generator exhaust generating new values, the iterator indicates it's done, and execution falls out of the `for` loop.

Let's look at one more code example in JavaScript before we move on to how the **spartan-jasync** library achieves a very similar capability for the Java programming language - but entirely implemented as a library.

### JavaScript `runner` helper for using generator functions

A helpful pattern for utilizing JavaScript generator functions is the concept of the `runner` helper or utility. It's particularly useful for when a generator implementation is dealing with, say, retrieving data from a remote source. It would be desirable to have a clean way to apply a cleanup operation on the source and to handle exceptions that might get thrown in the generator's execution context.

Here is a basic idea of what a `runner` utility might be like:

```JavaScript
run( generator_function )
.then(
  function fullfilled() {
    // generator completed - do any cleanup or completion logic
  },
  function rejected( cause ) {
    // Ut-oh, something went wrong executing generator code
  }
);
```

In typical JavaScript implementations, a `Promise` would be returned (similar to a Java `future`) by the `runner` and that is how the generator's produced values are consumed.

### A Java `Runner` class helper

**spartan-jasync** will draw inspiration from the `runner` utility concept as a way to proceed to implement asynchronous generators for Java. Here is what a Java implementation looks like. The example generator produces the Fibonacci Sequence. It stops when it has generated a value that exceeds a specified ceiling:

```Java
final double ceiling = 200;

final Iterator<Double> iter = Runner.<Double>
  run((yield) -> {
    final double maxCeiling = ceiling;
    double j = 0, i = 1;
    yield.accept(j);
    if (maxCeiling <= j) return;
    yield.accept(i);
    if (maxCeiling == i) return;
    for(;;) {
      double tmp = i;
      i += j;
      j = tmp;
      if (i > maxCeiling) break;
      yield.accept(i);
    }
  })
  .then(() -> {
    System.out.println("DEBUG: completion lambda invoked - generator is done (or canceled)");
  })
  .elseHandleException(ex -> {
    System.out.println("DEBUG: exception handler lambda invoked for generator exception:");
    ex.printStackTrace(System.err);
  })
  .iterator();

final List<Double> results = Runner.stream(iter).collect(Collectors.toList());

results.forEach(System.out::println);
```

Lets break this down:

- Things start off by invoking `Runner.<Double>run(...)` static method
    - The `<Double>` generic parameter specifies the type that the generator will produce
- A generator is passed as a Java 8 lambda to this method
    - Notice that the generator lamda will itself be passed a `yield` lambda as its argument
- When the generator has a new value to pass to a consumer, it invokes the `yield` lambda, passing it the value
- The `run(...)` method uses builder pattern to chain a call to `then(...)`
    - The `then(...)` method accepts a completion lambda - when the generator stops and is done, this lambda gets called
- The `then(...)` method chains a call to `elseHandleException(...)`
    - The `elseHandleException(...)` accepts a lambda that will handle any exception that happens when executing generator code
    - The exception handling lambda will be passed a `Throwable` when invoked
- The `iterator()` method is the last in the chain, it returns an iterator object to the caller of the `run(...)` method

We then see that the Runner class has a `stream(...)` static method that is a utility for wrapping an iterator as a Java 8 stream:

- The `stream(...)` method accepts an iterator argument and returns a Java 8 stream object
- The stream is collected on, collecting values into a list that is returned
- The list elements are then printed out, one element per line

We could have also chosen to consume the iterator directly, like so:

```Java
while(iter.hasNext()) {
  System.out.println(iter.next());
}
```

Obviously it's nice to have the option, though, to consume whatever it is the generator is producing using Java 8 streams and all the flexibility that comes with that.

The net effect is that we have achieved a very similar pattern to our first example code above of a JavaScript generator where its iterator is consumed in a `for` loop.

The iterator returned by the runner is an object instance implementing `spartan.runner.Runner.Iterator` interface, which is derived from `java.util.Iterator`. A `cancel()` method is added in the extending iterator interface. The consumer of the iterator can invoke `cancel` if it has, say, found what it is looking for and doesn't need the generator to keep sending any more values:

```Java
final java.util.function.Predicate<Double> filterForSubset = d -> {
  if (d > 10000d && d < 1000000000d) {
    return true;
  }
  if (d > 1000000000d) {
    iter.cancel();
  }
  return false;
};

final List<Double> results = Runner.stream(iter)
  .filter(filterForSubset)
  .collect(Collectors.toList());
```

In this example we've added a stream filter that will cause collecting to capture a subrange of the values the generator produces. Notice that once it sees the stream values of interest, it invokes cancel on the iterator object - that will cause the asynchronously executing generator to halt and return. This is a highly contrived example, of course, but it illustrates the concept of how a consumer can tell the producer that it's had enough.

**NOTE:** The stream returned for a jasync generator is supplying values to the stream as they are being produced. A queue communicates values from the generator to the consumer. This means jasync streams will not support parallel operations. Stream processing will consume values in the sequence that the generator produces them - hence why the filter in the `cancel` example could know it had seen all values of interest and could thus tell the generator to halt producing.

### Implementation information

The **spartan-jasync** *generators-for-Java* library implementation executes a generator on a `java.util.concurrent.ForkJoinTask` as obtained from `ForkJoinPool.commonPool()` static method. A customized version of the `java.util.concurrent.ArrayBlockingQueue` is used for conveying generator values to a consumer - the customization is explained in the class JavaDoc comments. The blocking queue defaults to a size of 10 but a different queue depth can be specified as an argument to an overload of `Runner.run()`.

There is also a variation of the generator lambda that accpets an initial seed value. The use of this seed value feature is declared like so:

```Java
final double ceiling = args.length > 0 ? Double.parseDouble(args[0]) : 30 /* default */;

final Iterator<Double> iter = Runner.<Double, Double>
  run(ceiling, (seedValue, yield) -> {
    final double maxCeiling = seedValue;
    ...
  })
  ...
```

In this example the seed value happens to have the same type as the value type that is generated, but that does not have to be the case. Hence the first generic argument specifies the type of the seed value: `Runner.<Double, Double>run(...)`

### Build information

**spartan-jasync** library is a Maven project. There are no dependencies. This command line is sufficient to build it:

```
mvn package
```

#### Executing the `mainRun.main()` method from the command line

The **spartan-jasync** library has a class that implements a `main()` method for demo executing a Runner generator. This particular invocation will return 217 values:

```
java -ea -jar target/jasync-1.0-SNAPSHOT.jar 1000000000000000000000000000000000000000000000 stream-all
```

Execute with no arguments to get usage information. In addition to specifying a ceiling value, there are four different options for consuming of the produced values.