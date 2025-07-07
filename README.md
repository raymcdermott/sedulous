# Durable

An experiment in durable programming in Clojure.

## Rationale

There are frequently situations where one would like to reliably transact over two mediums that have different transactional models.

They should both succeed or fail.

Some motivating examples:
low stakes: record that an email was sent and send an email using an API.
medium stakes: record a new user registration and emit an HTML page.
high stakes: record payment for an order in your database and to transact that payment using a third-party API over the web. 

Before the web XA was used between database and other enterprise transactional systems. XA now is a virtually unused protocol. It had a complex spec which was non-trivial to demonstrate compliance and
despite the promise of interoperability between vendors, that never really bore fruit.

A more recent approach, as espoused by examples such as restate and temporal is to add reliability into code itself.

As you read into it, it becomes clear that it's another attempt at XA though now one that is proprietary. Even if they're open source, they're going their own way and want to ceate a monopoly.

So I wondered what it would look like - at a minimum - to make Clojure code reliable in this sense.

Specifically, I want to experiment with what it looks like we define and track effects in data.

I want to permit options in data such that we can stop the retries if we know it will never succeed due to
- exceptions that are impossible to recover from
- servers that have told you your call will never work
- other situations unknown

These behaviours are controlled by defaults, configuration or functions that the user provides.

Then we can add back-off / retry and other affordances around that mix.

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
- [ ] Track an effect using `spit`
- [ ] Track several effects using `spit`
- [ ] Track an effect that may throw using `spit`
- [ ] Track several effects that may throw using `spit`
- [ ] Track a mix of the above (`prn`, `spit`)
- [ ] Track an effect using `http`
- [ ] Track several effects using `http`
- [ ] Track an effect that may throw using `http`
- [ ] Track several effects that may throw using `http`
- [ ] Track a mix of the above (`prn`, `spit`, `http`)
- [ ] Have an optional number of retries
- [ ] Have an optional exception that will stop retries
- [ ] Have an optional function that will stop retries
- [ ] Use duratom rather than a native atom
- [ ] Async operations