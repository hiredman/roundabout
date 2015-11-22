# roundabout

A Clojure and Clojurescript library designed for flow control.

## Usage

Best used when you have two bits of core.async code communicating over
a medium that doesn't expose feedback. The test has a pretty good
example where an unbounded LinkedBlockingQueue stands in for such a
medium. The test example passes feedback via a shared core.async
channel, but it doesn't have to.

Ultimately what `sender` and `receiver` do is limit the number of
messages inflight between the input of sender and the output of
receiver to window-size.

## Further reading

https://code.google.com/p/chromium/issues/detail?id=492519
https://lists.w3.org/Archives/Public/public-whatwg-archive/2013Oct/0217.html

## License

Copyright © 2015 Kevin Downey

Distributed under the MIT license.
