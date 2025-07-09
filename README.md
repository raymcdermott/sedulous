# Sedulously

Sedulous:
- involving or accomplished with careful perseverance
- diligent in application or pursuit

An experiment in durable execution in Clojure.

## Rationale
There are frequently situations where one would like to reliably transact over two resources that have different transactional models.

They should both succeed or fail.

A motivating example: transact that payment using a third-party API over the web and record payment for an order in your database.

### Prior work
The general term is 2-Phase Commit (2PC) where there is a ceremony to coordinate commits over both resources.

Such coordination was adhoc and proprietary.

Before the web, XA was an interoperability standard that for 2PC that was used between database and other enterprise transactional systems. 

XA now is a virtually unused protocol. It had a complex spec which was non-trivial to demonstrate compliance and despite the promise of interoperability between vendors, it never really bore fruit.

### Modern work
A more recent approach, as espoused by examples such as restate.io and temporal.io is to add durable execution into lines of code.

Again the approach is proprietary and consequently tricky to mix and match.

The frameworks are large and have support for many languages.

They offer support for long-running workflows and both sync and async execution.

### Clojure
I wondered what it would look like - at a minimum - to provide reliable and durable Clojure code.

Specifically, I want to experiment with what it looks like we define and track effects in data.

Like those other frameworks, I want to permit options in data such that we can stop the retries when we know the work will never succeed due to
- exceptions that are impossible to recover from
- servers that have told you your call will never work
- other situations unknown to me but known to you

These behaviours are controlled by defaults, configuration or functions that the user provides.

Then we can add back-off / retry and other affordances around that mix.

### Scope
The current scope does not include coordinated services - only for individual modules.

I will aim to offer sync and async execution.

## Design

The design centres around transactions over a log.

Once the calls are registered with a log their eventual success or failure can be tracked.

## Implementation

First attempt is with an atom and a macro.

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
- [ ] Have an optional number of retries
- [ ] Have an optional exception that will stop retries
- [ ] Have an optional function that will stop retries
- [ ] Use duratom rather than a native atom
- [ ] Async operations