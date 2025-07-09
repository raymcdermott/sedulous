# Sedulous

- _Persevering and constant in effort or application; assiduous._
- _Diligent in application or pursuit; constant, steady, and persevering in business, or in endeavors to effect an object; steadily industrious; assiduous._

An experiment in durable and reliable execution in Clojure.

## Rationale
There are frequently situations where one would like to reliably transact over two resources that have different transactional models.

They should both succeed or fail.

A motivating example: transact that payment using a third-party API over the web and record payment for an order in your database.

### Background
The general term is 2-Phase Commit (2PC) where there is a ceremony to coordinate commits over both resources.

Early implementations to achieve coordination was adhoc and proprietary.

Before the web, XA was an interoperability standard that for 2PC that was used between database and other enterprise transactional systems. 

XA now is a virtually unused protocol. It had a complex spec which was non-trivial to demonstrate compliance and despite the promise of interoperability between vendors, it never really bore fruit.

### Modern work
A more recent approach, as espoused by examples such as restate.io and temporal.io is to add durable and reliable execution into lines of code.

All that is old is new again: these implementations are also adhoc and proprietary.

The frameworks are large and have support for many languages.

They offer support for long-running workflows and both sync and async execution.

### Clojure
I wondered what it would look like - at a minimum - to provide reliable and sedulous Clojure code.

Specifically, I want to experiment with what it looks like we define and track effects in data.

Like those other frameworks, I want to permit options such that we can stop the retries when we know the work will never succeed due to
- exceptions that are impossible to recover from (aka _terminal_ exceptions)
- servers that have told you your call will never work (aka irrecoverable errors e.g. HTTP status code 4xx and 5xx)
- other situations unknown to me but known to you (aka you give me a function, and I'll give you a response to check)

These behaviours are controlled by defaults, configuration or functions that the user provides.

Then we can add back-off / retry and other affordances around that mix.

### Scope
The current scope does **not** include coordinated services - only individual modules.

The solution will include sync and async execution.

## Design

The design centres around transactions over a log.

Once the calls are registered with a log their eventual success or failure can be tracked.

## Implementation

First attempt is with an atom and a macro.

## Notes / observations
- We can only use an atom for data changes ... run the effect outside the scope of the atom STM.
  - This is because the function passed to `swap!` is assumed, by design, to be free of side effects as it may be called several times.
- The effect functions must be idempotent because they maybe called several times.
  - Our main guarantee is that each effect will be called and the transaction will only be completed when they all succeed.
  - If there is a terminal error, that will prevent one function from completing.
  - We need some configuration / behaviour for other functions in the face of that fail. 
    - Sync and async, where the function calls are serialised, will be the same. Async parallel calls would be different.
- There is no rollback option.

## TODO
- [X] Track an effect using `prn`
- [X] Track several effects using `prn`
- [X] Track an effect that may throw using `prn`
- [X] Track several effects that may throw using `prn`
- [X] Track a mix of the above (`prn`)
- [X] Track an effect using `spit`
- [X] Track several effects using `spit`
- [X] Track an effect that may throw using `spit`
- [X] Track several effects that may throw using `spit`
- [X] Track a mix of the above (`prn`, `spit`)
- [X] Track an effect using `http`
- [X] Track several effects using `http`
- [X] Track an effect that may throw using `http`
- [X] Track several effects that may throw using `http`
- [X] Track a mix of the above (`prn`, `spit`, `http`)
- [ ] Prove that the execution survives machine failure 
- [X] Optional number of retries
- [ ] Optional exception(s) that will stop retries
- [ ] Optional function that will check response & stop retries
- [ ] Use duratom rather than a native atom
- [ ] Async operations