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

The new special syntax of the `*` asterisk character preceding the function name (indicates this function returns an iterator when invoked) and then the use of `yield` keyword for supplying *generated* values to a consumer of the returned iterator.

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

