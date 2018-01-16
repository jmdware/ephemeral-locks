ephemeral-locks balances parallelism with thread-safety by assigning different locks to different
keys. This allows threads operating on different keys to simultaneously execute the critical
section while threads operating on the same key execute the critical section serially.

Memory footprint grows with the number of active keys. Underlying lock instances are discarded when
their keys are no longer active (when no threads are requesting the lock for a key).

Keys must correctly implement `equals(Object)` and `hashCode()`.

Minimum requirements:

* java8

# Dependency Coordinates

Add these dependency coordinates, or an equivalent, to your project to begin using this library.

*maven*

```xml
<dependency>
    <groupId>org.jmdware</groupId>
    <artifactId>ephemeral-locks</artifactId>
    <version>1.0.0</version>
</dependency>
```

*gradle*

```
compile "org.jmdware:ephemeral-locks:1.0.0"
```

*leiningen*

```
[org.jmdware/ephemeral-locks "1.0.0"]
```

# Usage

Say we want to prevent multiple threads from simultaneously accessing a directory of files.
Different threads may simultaneously access different directories, however. In this case, the
resource we are protecting from unwanted, concurrent access is identified by a directory path.

```java
/**
 * Guards against concurrent access to any given directory.
 */
private final EphemeralLocks dirLocks = new EphemeralLocks();

...

void someMethod(...) {
    // The directory of files, relative to some base directory.
    String relativePath = ...;

    File dir = new File(BASE_DIR, relativePath);

    // try-with-resources recommended to ensure unlock occurs.
    try (Handle ignored = dirLocks.lock(dir.getAbsolutePath())) {
        // critical section - do sensitive operations here
       ...
    }
}
```

Any object that correctly implements `equals(Object)` and `hashCode()` may be used as the resource
key.

`try-finally` works, as well.

```java
Handle lockHandle = dirLocks.lock(...);

try {
    // critical section - do sensitive operations here
    ...
} finally {
    lockHandle.close();
}
```

# License

Copyright (c) 2018 David Ha

Published under Apache Software License 2.0, see [LICENSE](LICENSE).
