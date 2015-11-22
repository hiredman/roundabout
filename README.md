# roundabout

A Clojure library designed for flow control.

## Usage

Best used when you have two bits of core.async code communicating over
a medium that doesn't expose feedback. The test has a pretty good
example where an unbounded LinkedBlockingQueue stands in for such a
medium. The test example passes feedback via a shared core.async
channel, but it doesn't have to.

## License

Copyright © 2015 Kevin Downey

Distributed under the MIT license.
