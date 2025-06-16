# Durable

An experiment in durable programming in Clojure.

## Rationale

There are frequently situations where one would like to transact over two mediums that do not offer XA transactional capabilities.

XA itself is an old, somewhat despised protocol.

The new kid in town, as espoused by restate and temporal is to add reliability into code itself.

As you read into it, it's clearly another attempt at XA though now one that is proprietary.

Even if they're open source, they're going their own way.

So I wondered what it would look like - at a minimum - in Clojure.

## Design

The main design centres around a log.

Once the calls are registered with a log their success or failure can be tracked.

## Implementation

An atom and a macro.